package io.github.oliviercailloux.gitjfs.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.UnmodifiableIterator;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.jgit.JGit;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests about actual reading from a repo, using the Files API.
 */
public class GitDiffTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GitDiffTests.class);

  @Test
  void testDiff() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
      final GitFileSystemProvider provider = GitFileSystemProviderImpl.getInstance();
      try (GitFileSystem gitFs = provider.newFileSystemFromDfsRepository(repo)) {
        final GitPathRootSha p0 = gitFs.getPathRoot(commits.get(0));
        final GitPathRootSha p1 = gitFs.getPathRoot(commits.get(1));
        final GitPathRootSha p2 = gitFs.getPathRoot(commits.get(2));

        assertEquals(ImmutableSet.of(), gitFs.diff(p0, p0));
        {
          final ImmutableSet<DiffEntry> diffs01 = gitFs.diff(p0, p1);
          final DiffEntry diff01 = Iterables.getOnlyElement(diffs01);
          assertEquals(ChangeType.ADD, diff01.getChangeType());
          assertEquals("file2.txt", diff01.getNewPath());
        }
        {
          final ImmutableSet<DiffEntry> diffs10 = gitFs.diff(p1, p0);
          final DiffEntry diff10 = Iterables.getOnlyElement(diffs10);
          assertEquals(ChangeType.DELETE, diff10.getChangeType());
          assertEquals("file2.txt", diff10.getOldPath());
        }
        {
          final ImmutableSet<DiffEntry> diffs12 = gitFs.diff(p1, p2);
          final UnmodifiableIterator<DiffEntry> iterator = diffs12.iterator();
          final DiffEntry diff12first = iterator.next();
          final DiffEntry diff12second = iterator.next();
          assertEquals(ChangeType.ADD, diff12first.getChangeType());
          assertEquals("dir/file.txt", diff12first.getNewPath());
          assertEquals(ChangeType.MODIFY, diff12second.getChangeType());
          assertEquals("file2.txt", diff12second.getOldPath());
          assertEquals("file2.txt", diff12second.getNewPath());
        }
        {
          final ImmutableSet<DiffEntry> diffs02 = gitFs.diff(p0, p2);
          final UnmodifiableIterator<DiffEntry> iterator = diffs02.iterator();
          final DiffEntry diff02first = iterator.next();
          final DiffEntry diff02second = iterator.next();
          assertFalse(iterator.hasNext());
          assertEquals(ChangeType.ADD, diff02first.getChangeType());
          assertEquals("dir/file.txt", diff02first.getNewPath());
          assertEquals(ChangeType.ADD, diff02second.getChangeType());
          assertEquals("file2.txt", diff02second.getNewPath());
        }
      }
    }
  }
}
