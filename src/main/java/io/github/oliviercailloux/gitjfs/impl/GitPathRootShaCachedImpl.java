package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import io.github.oliviercailloux.gitjfs.Commit;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import java.util.Optional;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitPathRootShaCachedImpl extends GitPathRootShaImpl implements GitPathRootShaCached {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GitPathRootShaCachedImpl.class);

  protected GitPathRootShaCachedImpl(GitFileSystemImpl fileSystem, GitRev gitRev,
      RevCommit commit) {
    super(fileSystem, gitRev, Optional.of(commit));
    checkArgument(gitRev.isCommitId());
    checkArgument(commit.getId().equals(gitRev.getCommitId()));
  }

  @Override
  public GitPathRootShaCachedImpl toSha() {
    return this;
  }

  @Override
  public GitPathRootShaCachedImpl toShaCached() {
    return this;
  }

  @Override
  RevCommit getRevCommit() {
    verify(!revCommit.isEmpty());
    return revCommit.get();
  }

  @Override
  public Commit getCommit() {
    return getCommit(getRevCommit());
  }
}
