package io.sentry.spring.tracing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringJUnitConfig(SentryTransactionAdviceTest.Config::class)
class SentryTransactionAdviceTest {

    @Autowired
    lateinit var sampleService: SampleService

    @Autowired
    lateinit var classAnnotatedSampleService: ClassAnnotatedSampleService

    @Autowired
    lateinit var classAnnotatedWithOperationSampleService: ClassAnnotatedWithOperationSampleService

    @Autowired
    lateinit var hub: IHub

    @BeforeTest
    fun setup() {
        whenever(hub.startTransaction(any<String>(), any(), eq(true))).thenAnswer { io.sentry.SentryTracer(TransactionContext(it.arguments[0] as String, it.arguments[1] as String), hub) }
    }

    @Test
    fun `creates transaction around method annotated with @SentryTransaction`() {
        sampleService.methodWithTransactionNameSet()
        verify(hub).captureTransaction(check {
            assertThat(it.transaction).isEqualTo("customName")
            assertThat(it.contexts.trace!!.operation).isEqualTo("bean")
        })
    }

    @Test
    fun `when @SentryTransaction has no name set, sets transaction name as className dot methodName`() {
        sampleService.methodWithoutTransactionNameSet()
        verify(hub).captureTransaction(check {
            assertThat(it.transaction).isEqualTo("SampleService.methodWithoutTransactionNameSet")
            assertThat(it.contexts.trace!!.operation).isEqualTo("op")
        })
    }

    @Test
    fun `when transaction is already active, does not start new transaction`() {
        val scope = Scope(SentryOptions())
        scope.setTransaction(io.sentry.SentryTracer(TransactionContext("aTransaction", "op"), hub))

        whenever(hub.configureScope(any())).thenAnswer {
            (it.arguments[0] as ScopeCallback).run(scope)
        }

        sampleService.methodWithTransactionNameSet()
        verify(hub, times(0)).captureTransaction(any(), any())
    }

    @Test
    fun `creates transaction around method in class annotated with @SentryTransaction`() {
        classAnnotatedSampleService.hello()
        verify(hub).captureTransaction(check {
            assertThat(it.transaction).isEqualTo("ClassAnnotatedSampleService.hello")
            assertThat(it.contexts.trace!!.operation).isEqualTo("op")
        })
    }

    @Test
    fun `creates transaction with operation set around method in class annotated with @SentryTransaction`() {
        classAnnotatedWithOperationSampleService.hello()
        verify(hub).captureTransaction(check {
            assertThat(it.transaction).isEqualTo("ClassAnnotatedWithOperationSampleService.hello")
            assertThat(it.contexts.trace!!.operation).isEqualTo("my-op")
        })
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(SentryTracingConfiguration::class)
    open class Config {

        @Bean
        open fun sampleService() = SampleService()

        @Bean
        open fun classAnnotatedSampleService() = ClassAnnotatedSampleService()

        @Bean
        open fun classAnnotatedWithOperationSampleService() = ClassAnnotatedWithOperationSampleService()

        @Bean
        open fun hub() = mock<IHub>()
    }

    open class SampleService {

        @SentryTransaction(name = "customName", operation = "bean")
        open fun methodWithTransactionNameSet() = Unit

        @SentryTransaction(operation = "op")
        open fun methodWithoutTransactionNameSet() = Unit
    }

    @SentryTransaction(operation = "op")
    open class ClassAnnotatedSampleService {

        open fun hello() = Unit
    }

    @SentryTransaction(operation = "my-op")
    open class ClassAnnotatedWithOperationSampleService {

        open fun hello() = Unit
    }
}
