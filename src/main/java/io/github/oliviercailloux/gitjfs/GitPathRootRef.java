package io.github.oliviercailloux.gitjfs;

/**
 * A {@link GitPathRoot} containing a git ref.
 */
public interface GitPathRootRef extends GitPathRoot {
  /**
   * Returns {@code false}.
   *
   * @return {@code false}
   * @deprecated No reason to call this.
   */
  @Override
  @Deprecated
  boolean isCommitId();

  /**
   * Returns {@code true}.
   *
   * @return {@code true}
   * @deprecated No reason to call this.
   */
  @Override
  @Deprecated
  boolean isRef();
}
