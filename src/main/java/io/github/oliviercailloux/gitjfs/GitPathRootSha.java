package io.github.oliviercailloux.gitjfs;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

public interface GitPathRootSha extends GitPathRoot {

  @Override
  GitPathRootSha toSha();

  @Override
  GitPathRootShaCached toShaCached() throws IOException, NoSuchFileException;

  /**
   * Returns {@code true}.
   *
   * @return {@code true}
   * @deprecated No reason to call this.
   */
  @Override
  @Deprecated
  boolean isCommitId();

  /**
   * Returns {@code false}.
   *
   * @return {@code false}
   * @deprecated No reason to call this.
   */
  @Override
  @Deprecated
  boolean isRef();

  /**
   * Throws an exception.
   *
   * @return nothing
   * @deprecated No reason to call this.
   * @throws IllegalStateException always
   */
  @Override
  @Deprecated
  String getGitRef() throws IllegalStateException;
}
