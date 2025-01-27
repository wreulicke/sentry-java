package io.sentry.spring.tracing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.CustomSamplingContext
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.protocol.SentryId
import io.sentry.spring.SentryRequestResolver
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.assertj.core.api.Assertions.assertThat
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping

class SentryTracingFilterTest {
    private class Fixture {
        val hub = mock<IHub>()
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()
        val sentryRequestResolver = spy(SentryRequestResolver(hub))
        val transactionNameProvider = spy(TransactionNameProvider())

        init {
            whenever(hub.options).thenReturn(SentryOptions())
        }

        fun getSut(isEnabled: Boolean = true, sentryTraceHeader: String? = null): SentryTracingFilter {
            request.requestURI = "/product/12"
            request.method = "POST"
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/product/{id}")
            if (sentryTraceHeader != null) {
                request.addHeader("sentry-trace", sentryTraceHeader)
                whenever(hub.startTransaction(any(), any<CustomSamplingContext>(), eq(true))).thenAnswer { SentryTracer(it.arguments[0] as TransactionContext, hub) }
            }
            response.status = 200
            whenever(hub.startTransaction(any(), any(), any(), eq(true))).thenAnswer { SentryTracer(TransactionContext(it.arguments[0] as String, it.arguments[1] as String), hub) }
            whenever(hub.isEnabled).thenReturn(isEnabled)
            return SentryTracingFilter(hub, sentryRequestResolver, transactionNameProvider)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `sets the request on the scope`() {
        val filter = fixture.getSut()

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.chain).doFilter(fixture.request, fixture.response)
        verify(fixture.hub, times(1)).configureScope(check {
            val scope = mock<Scope>()
            it.run(scope)
            verify(scope).setRequest(check { request ->
                assertThat(request.url).isEqualTo("http://localhost/product/12")
            })
        })
    }

    @Test
    fun `creates transaction around the request`() {
        val filter = fixture.getSut()

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).startTransaction(eq("POST /product/12"), eq("http.server"), check<CustomSamplingContext> {
            assertNotNull(it["request"])
            assertTrue(it["request"] is HttpServletRequest)
        }, eq(true))
        verify(fixture.chain).doFilter(fixture.request, fixture.response)
        verify(fixture.hub).captureTransaction(check {
            assertThat(it.transaction).isEqualTo("POST /product/{id}")
            assertThat(it.contexts.trace!!.status).isEqualTo(SpanStatus.OK)
            assertThat(it.contexts.trace!!.operation).isEqualTo("http.server")
        })
    }

    @Test
    fun `when sentry trace is not present, transaction does not have parentSpanId set`() {
        val filter = fixture.getSut()

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).captureTransaction(check {
            assertThat(it.contexts.trace!!.parentSpanId).isNull()
        })
    }

    @Test
    fun `when sentry trace is present, transaction has parentSpanId set`() {
        val parentSpanId = SpanId()
        val filter = fixture.getSut(sentryTraceHeader = "${SentryId()}-$parentSpanId-1")

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.hub).captureTransaction(check {
            assertThat(it.contexts.trace!!.parentSpanId).isEqualTo(parentSpanId)
        })
    }

    @Test
    fun `when hub is disabled, components are not invoked`() {
        val filter = fixture.getSut(isEnabled = false)

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        verify(fixture.chain).doFilter(fixture.request, fixture.response)

        verify(fixture.hub).isEnabled
        verifyNoMoreInteractions(fixture.hub)
        verifyZeroInteractions(fixture.sentryRequestResolver)
        verifyZeroInteractions(fixture.transactionNameProvider)
    }
}
