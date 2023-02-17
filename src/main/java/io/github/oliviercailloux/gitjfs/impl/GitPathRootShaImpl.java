package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitPathRootShaImpl extends GitPathRootImpl implements GitPathRootSha {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GitPathRootShaImpl.class);

  protected Optional<RevCommit> revCommit;

  protected GitPathRootShaImpl(GitFileSystemImpl fileSystem, GitRev gitRev,
      Optional<RevCommit> commit) {
    super(fileSystem, gitRev);
    checkArgument(gitRev.isCommitId());
    revCommit = commit.isPresent() ? commit : null;
  }

  @Deprecated
  @Override
  public GitPathRootShaImpl toSha() {
    return this;
  }

  @Override
  public GitPathRootShaCachedImpl toShaCached() throws IOException, NoSuchFileException {
    return new GitPathRootShaCachedImpl(getFileSystem(), toStaticRev(), getRevCommit());
  }

  /**
   * Returns empty when this sha does not exist, or is not a commit id.
   */
  private Optional<RevCommit> tryGetRevCommit() throws IOException {
    if (revCommit == null) {
      LOGGER.debug("Trying to get rev commit of {}.", toString());

      try {
        final RevCommit theOne = getFileSystem().getRevCommit(getStaticCommitId());
        LOGGER.debug("Returning with rev commit.");
        revCommit = Optional.of(theOne);
      } catch (IncorrectObjectTypeException e) {
        LOGGER.info("Tried to access a non-commit as a commit: " + e + ".");
        revCommit = Optional.empty();
      } catch (MissingObjectException e) {
        LOGGER.info("Tried to access a missing commit: " + e + ".");
        revCommit = Optional.empty();
      }
    }
    return revCommit;
  }

  @Override
  RevCommit getRevCommit() throws IOException, NoSuchFileException {
    final Optional<RevCommit> commit = tryGetRevCommit();
    if (commit.isEmpty()) {
      throw new NoSuchFileException(toString());
    }
    return commit.get();
  }
}
