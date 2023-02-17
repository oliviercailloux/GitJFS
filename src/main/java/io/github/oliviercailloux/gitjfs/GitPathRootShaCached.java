package io.github.oliviercailloux.gitjfs;

public interface GitPathRootShaCached extends GitPathRootSha {

  /**
   * Returns this path.
   *
   * @return itself
   *
   * @deprecated No reason to call this.
   */
  @Override
  @Deprecated
  GitPathRootShaCached toShaCached();

  @Override
  Commit getCommit();
}
