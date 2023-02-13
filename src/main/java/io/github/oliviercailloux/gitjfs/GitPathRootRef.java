package io.github.oliviercailloux.gitjfs;

import org.eclipse.jgit.lib.ObjectId;

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
   * Throws an exception.
   *
   * @return nothing
   * @deprecated No reason to call this.
   * @throws IllegalStateException always
   */
  @Override
  @Deprecated
  ObjectId getStaticCommitId() throws IllegalStateException;

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
