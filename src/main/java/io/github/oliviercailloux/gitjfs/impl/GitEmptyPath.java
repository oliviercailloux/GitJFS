package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Path;

/**
 * An empty path. Each git file system is associated to a unique empty path. This class should
 * therefore have a unique instance per git file system. Use {@link GitFileSystemImpl#emptyPath}
 * rather than creating a new one.
 */
class GitEmptyPath extends GitRelativePath {
  private GitPathRootImpl absoluteEquivalent;

  GitEmptyPath(GitPathRootImpl absoluteEquivalent) {
    this.absoluteEquivalent = checkNotNull(absoluteEquivalent);
    checkArgument(
        absoluteEquivalent.getRoot().toStaticRev().equals(GitPathRootImpl.DEFAULT_GIT_REF));
  }

  /**
   * Returns a git path root referring to the main branch of the git file system associated to this
   * path.
   */
  @Override
  public GitPathRootImpl toAbsolutePath() {
    return absoluteEquivalent;
  }

  @Override
  Path getInternalPath() {
    return GitFileSystemImpl.JIM_FS_EMPTY;
  }
}
