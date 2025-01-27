package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import io.sentry.util.CollectionUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class SentryRuntime implements IUnknownPropertiesConsumer, Cloneable {
  public static final String TYPE = "runtime";

  /** Runtime name. */
  private String name;
  /** Runtime version string. */
  private String version;
  /**
   * Unprocessed runtime info.
   *
   * <p>An unprocessed description string obtained by the runtime. For some well-known runtimes,
   * Sentry will attempt to parse `name` and `version` from this string, if they are not explicitly
   * given.
   */
  private String rawDescription;

  @SuppressWarnings("unused")
  private Map<String, @NotNull Object> unknown;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getRawDescription() {
    return rawDescription;
  }

  public void setRawDescription(String rawDescription) {
    this.rawDescription = rawDescription;
  }

  @TestOnly
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, @NotNull Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  /**
   * Clones a SentryRuntime aka deep copy
   *
   * @return the cloned SentryRuntime
   * @throws CloneNotSupportedException if object is not cloneable
   */
  @Override
  public @NotNull SentryRuntime clone() throws CloneNotSupportedException {
    final SentryRuntime clone = (SentryRuntime) super.clone();

    clone.unknown = CollectionUtils.shallowCopy(unknown);

    return clone;
  }
}
