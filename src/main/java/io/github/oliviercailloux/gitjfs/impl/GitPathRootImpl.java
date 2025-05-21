package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.gitjfs.Commit;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl.FollowLinksBehavior;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl.GitObject;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GitPathRootImpl extends GitAbsolutePath implements GitPathRoot {
  public static final GitRev DEFAULT_GIT_REF = GitRev.shortRef("refs/heads/main");

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GitPathRootImpl.class);

  static GitPathRootImpl given(GitFileSystemImpl gitFs, GitRev gitRev) {
    if (gitRev.isCommitId()) {
      return new GitPathRootShaImpl(gitFs, gitRev, Optional.empty());
    }
    return new GitPathRootRefImpl(gitFs, gitRev);
  }

  private final GitFileSystemImpl fileSystem;

  private final GitRev gitRev;

  /**
   * This is not a git rev, although it shares some similar characteristics with a git rev. Its
   * string form ends with //, whereas the string form of a git rev ends with a single /; and it
   * never equals a git rev.
   */
  protected GitPathRootImpl(GitFileSystemImpl fileSystem, GitRev gitRev) {
    this.fileSystem = checkNotNull(fileSystem);
    this.gitRev = checkNotNull(gitRev);
  }

  @Override
  Path getInternalPath() {
    return GitFileSystemImpl.JIM_FS_SLASH;
  }

  @Override
  public GitFileSystemImpl getFileSystem() {
    return fileSystem;
  }

  @Override
  @Deprecated
  public GitPathRootImpl getRoot() {
    return this;
  }

  @Override
  @Deprecated
  public GitPathRootImpl toAbsolutePath() {
    return this;
  }

  @Override
  GitEmptyPath toRelativePath() {
    return fileSystem.emptyPath;
  }

  GitRev toStaticRev() {
    return gitRev;
  }

  @Override
  @Deprecated
  public GitPathRootImpl getParent() {
    verify(getInternalPath().getParent() == null);
    return null;
  }

  @Override
  @Deprecated
  public GitPathRootImpl getFileName() {
    verify(super.getFileName() == null);
    return null;
  }

  @Override
  public boolean isRef() {
    return gitRev.isRef();
  }

  @Override
  public boolean isCommitId() {
    return gitRev.isCommitId();
  }

  @Override
  public String getGitRef() {
    /*
     * Returning a JGit Ref here is another possibility. But 1) a Ref is much more complex than
     * required at this level: JGit’s Ref objects include symbolic refs, they may be peeled or non
     * peeled, and they may refer to git objects that are not commits. Git refs as considered here
     * are only direct pointers to commits. And 2) a Ref may return a null commit id; I prefer to
     * guarantee that this library never returns null. (Admittedly, I have to allow for exceptions
     * when using third party objects, for example Map can return null, but I prefer to reduce
     * exceptions as much as possible.)
     */
    return gitRev.getGitRef();
  }

  @Override
  public ObjectId getStaticCommitId() {
    return gitRev.getCommitId();
  }

  abstract RevCommit getRevCommit() throws IOException, NoSuchFileException;

  @Override
  RevTree getRevTree(boolean followLinks) throws IOException, NoSuchFileException {
    return getRevTree();
  }

  RevTree getRevTree() throws IOException, NoSuchFileException {
    return getRevCommit().getTree();
  }

  @Override
  public Commit getCommit() throws IOException, NoSuchFileException {
    /*
     * I considered using dynamic fetching in the returned object: if the user only wants the commit
     * id, we don’t need to parse the commit, thus, we could parse the commit on-demand. But this
     * introduces complexities (we have to document that sometimes, the Commit is bound to a file
     * system and should be fetched while the fs is still open), and we don’t gain much: I can’t
     * imagine cases where the user will want a vast series of commit ids without having to parse
     * them. Mostly, a vast series of commits would come from a desire to browse (part of) the
     * history, and this requires accessing the parent-of relation, which requires parsing the
     * commit.
     */
    /*
     * NB this exists-based approach (rather than Optional on getCommit) seems adequate because most
     * of the time, the user will use commit ids, coming from the history or the set of roots of
     * this fs, and thus it is known that the related commit exists. Similarly, if the user uses
     * some ref, she must have learned from somewhere that this ref exists in this repo. Only if the
     * user accesses the main branch should she test its existence, and even there, perhaps she
     * knows that this branch exists (e.g. her own repositories).
     */
    final RevCommit revCommit = getRevCommit();
    return getCommit(revCommit);
  }

  Commit getCommit(RevCommit revCommit) {
    final PersonIdent authorIdent = revCommit.getAuthorIdent();
    final PersonIdent committerIdent = revCommit.getCommitterIdent();
    return Commit.from(revCommit, authorIdent.getName(), authorIdent.getEmailAddress(),
        getCreationTime(authorIdent), committerIdent.getName(), committerIdent.getEmailAddress(),
        getCreationTime(committerIdent), ImmutableList.copyOf(revCommit.getParents()));
  }

  @Override
  public ImmutableList<GitPathRootSha> getParentCommits() throws IOException, NoSuchFileException {
    if (fileSystem.computedGraph()) {
      return ImmutableList.copyOf(fileSystem.graph().predecessors(this.toShaCached()));
    }

    /*
     * Design choice. We need to parse this commit to get its parents (so the caller should call
     * toShaCached() then getParents if she’s interested in caching this one for free), but this does
     * not imply that we can return a list of <ShaCached>: this would require also reading the
     * parents of the parents…
     */
    final RevCommit revCommit = getRevCommit();
    final ImmutableList<RevCommit> parents = ImmutableList.copyOf(revCommit.getParents());

    final ImmutableList.Builder<GitPathRootSha> builder = ImmutableList.builder();
    for (ObjectId parentId : parents) {
      builder.add(getFileSystem().getPathRoot(parentId));
    }
    return builder.build();
  }

  @Override
  public abstract GitPathRootShaImpl toSha() throws IOException, NoSuchFileException;

  @Override
  public GitPathRootShaCachedImpl toShaCached() throws IOException, NoSuchFileException {
    return toSha().toShaCached();
  }

  @Override
  GitObject getGitObject(FollowLinksBehavior behavior) throws NoSuchFileException, IOException {
    return GitObject.given(GitFileSystemImpl.JIM_FS_SLASH, getRevTree(), FileMode.TREE);
  }

  private static ZonedDateTime getCreationTime(PersonIdent ident) {
    final Instant creationInstant = ident.getWhenAsInstant();
    final ZoneId creationZone = ident.getZoneId();
    final ZonedDateTime creationTime =
        ZonedDateTime.ofInstant(creationInstant, creationZone);
    return creationTime;
  }
}
