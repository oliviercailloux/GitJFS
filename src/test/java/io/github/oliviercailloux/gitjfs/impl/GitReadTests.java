package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Verify.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.PeekingIterator;
import com.google.common.graph.MutableGraph;
import io.github.oliviercailloux.git.common.GitUri;
import io.github.oliviercailloux.git.common.RepositoryCoordinates;
import io.github.oliviercailloux.git.factory.FactoGit;
import io.github.oliviercailloux.git.factory.JGit;
import io.github.oliviercailloux.gitjfs.AbsoluteLinkException;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.gitjfs.PathCouldNotBeFoundException;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl.GitStringObject;
import io.github.oliviercailloux.jaris.graphs.GraphUtils;
import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests about actual reading from a repo, using the Files API.
 */
public class GitReadTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GitReadTests.class);

  static FileRepository download(GitUri uri, Path repositoryDirectory)
      throws GitAPIException, IOException {
    verify(Files.list(repositoryDirectory).count() == 0);
    final CloneCommand cloneCmd = Git.cloneRepository();
    cloneCmd.setURI(uri.asString());
    cloneCmd.setBare(true);
    final File dest = repositoryDirectory.toFile();
    cloneCmd.setDirectory(dest);
    LOGGER.info("Cloning {} to {}.", uri, dest);
    Git git = cloneCmd.call();
    return (FileRepository) git.getRepository();
  }

  public static void main(String[] args) throws Exception {
    try (Repository repo = new FileRepository("/tmp/ploum/.git")) {
      JGit.createRepoWithLink(repo);
    }
  }

  @Test
  void testReadFiles() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
      final GitFileSystemProvider provider = GitFileSystemProviderImpl.getInstance();
      try (GitFileSystem gitFs = provider.newFileSystemFromDfsRepository(repo)) {
        assertEquals("Hello, world", Files.readString(gitFs.getRelativePath("file1.txt")));
        assertEquals("Hello, world", Files.readString(gitFs.getRelativePath("./file1.txt")));
        assertEquals("Hello, world",
            Files.readString(gitFs.getRelativePath(".", "dir", "..", "/file1.txt")));
        assertEquals("Hello, world", Files
            .readString(gitFs.getRelativePath(".", "dir", "..", "/file1.txt").toAbsolutePath()));
        assertEquals("Hello from sub dir",
            Files.readString(gitFs.getRelativePath("dir", "file.txt")));
        assertEquals("Hello, world",
            Files.readString(gitFs.getRelativePath("file1.txt").toAbsolutePath()));
        assertThrows(NoSuchFileException.class,
            () -> Files.readString(gitFs.getAbsolutePath(commits.get(0), "file2.txt")));
        assertEquals("Hello again",
            Files.readString(gitFs.getAbsolutePath(commits.get(1), "file2.txt")));
        assertEquals("I insist", Files.readString(gitFs.getRelativePath("file2.txt")));
        assertEquals("I insist",
            Files.readString(gitFs.getRelativePath("file2.txt").toAbsolutePath()));
        assertThrows(NoSuchFileException.class,
            () -> Files.newByteChannel(gitFs.getRelativePath("ploum.txt")));
        try (SeekableByteChannel dirChannel = Files.newByteChannel(gitFs.getRelativePath())) {
          assertThrows(IOException.class, () -> dirChannel.size());
        }
        assertThrows(IOException.class, () -> Files.readString(gitFs.getRelativePath()));
        assertThrows(IOException.class, () -> Files.readString(gitFs.getRelativePath("dir")));
      }
    }
  }

  @Test
  void testCreateBasic() throws Exception {
    try (DfsRepository repo = FactoGit.createBasicRepo()) {
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        assertEquals(1, gitFs.graph().nodes().size());
      }
    }
  }

  @Test
  void testReadLinks() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithLink(repo);

      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        assertEquals("Hello, world",
            Files.readString(gitFs.getAbsolutePath(commits.get(0), "/file1.txt")));
        assertEquals("Hello, world",
            Files.readString(gitFs.getAbsolutePath(commits.get(0), "/link.txt")));
        assertThrows(PathCouldNotBeFoundException.class,
            () -> Files.readString(gitFs.getAbsolutePath(commits.get(0), "/absolute link")));
        assertEquals("Hello instead",
            Files.readString(gitFs.getAbsolutePath(commits.get(1), "/link.txt")));
        assertEquals("Hello instead",
            Files.readString(gitFs.getAbsolutePath(commits.get(2), "/dir/link")));
        assertEquals("Hello instead",
            Files.readString(gitFs.getAbsolutePath(commits.get(2), "/dir/linkToParent/dir/link")));
        assertEquals("Hello instead", Files.readString(
            gitFs.getAbsolutePath(commits.get(2), "/dir/linkToParent/dir/linkToParent/dir/link")));
        assertFalse(Files.exists(gitFs.getAbsolutePath(commits.get(3), "/dir/link")));
        assertFalse(
            Files.exists(gitFs.getAbsolutePath(commits.get(3), "/dir/linkToParent/dir/link")));
        assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(3), "/dir/link"),
            LinkOption.NOFOLLOW_LINKS));
        assertFalse(
            Files.exists(gitFs.getAbsolutePath(commits.get(3), "/dir/linkToParent/dir/link"),
                LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(gitFs.getAbsolutePath(commits.get(2), "/dir/cyclingLink")));
        assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(2), "/dir/cyclingLink"),
            LinkOption.NOFOLLOW_LINKS));

        assertThrows(NotLinkException.class,
            () -> Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(0), "/file1.txt")));
        assertEquals(gitFs.getRelativePath("file1.txt"),
            Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(0), "/link.txt")));
        assertThrows(NoSuchFileException.class,
            () -> Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(0), "/notexists")));
        assertEquals(gitFs.getRelativePath("../link.txt"),
            Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(2), "/dir/./link")));
        assertEquals(gitFs.getRelativePath("../dir/cyclingLink"),
            Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(2), "/dir/cyclingLink")));
        final AbsoluteLinkException thrown = assertThrows(AbsoluteLinkException.class,
            () -> Files.readSymbolicLink(gitFs.getAbsolutePath(commits.get(0), "/absolute link")));
        assertEquals(gitFs.getAbsolutePath(commits.get(0), "/absolute link"), thrown.getLinkPath());
        assertEquals(Path.of("/absolute"), thrown.getTarget());
      }
    }
  }

  @Test
  void testExists() throws Exception {
    try (
        DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repository);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repository)) {
        assertTrue(Files.exists(gitFs.getRelativePath()));
        assertTrue(Files.exists(gitFs.getRelativePath().toAbsolutePath()));
        assertTrue(Files.exists(gitFs.getAbsolutePath("/refs/heads/main/")));
        assertFalse(Files.exists(gitFs.getAbsolutePath("/refs/nothing/")));
        assertFalse(Files.exists(gitFs.getPathRoot(ObjectId.zeroId())));
        assertTrue(Files.exists(gitFs.getPathRoot(commits.get(0))));
        assertFalse(Files.exists(gitFs.getAbsolutePath(commits.get(0), "/ploum.txt")));
        assertTrue(Files.exists(gitFs.getAbsolutePath(commits.get(0), "/file1.txt")));
        assertFalse(Files.exists(gitFs.getRelativePath("ploum.txt")));
        assertFalse(Files.exists(gitFs.getRelativePath("ploum.txt").toAbsolutePath()));
        assertFalse(Files.exists(gitFs.getRelativePath("dir/ploum.txt")));
        assertTrue(Files.exists(gitFs.getRelativePath("file1.txt")));
        assertTrue(Files.exists(gitFs.getRelativePath("dir")));
        assertTrue(Files.exists(gitFs.getRelativePath("dir/file.txt")));
      }
    }
  }

  /**
   * Just some experiments with a TreeWalk.
   */
  @Test
  void testTreeWalk() throws Exception {
    try (
        DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      JGit.createRepoWithSubDir(repository);
      try (GitDfsFileSystemImpl gitFs = ((GitDfsFileSystemImpl) GitFileSystemProviderImpl
          .getInstance().newFileSystemFromDfsRepository(repository))) {
        final GitPathRootImpl rootPath =
            (GitPathRootImpl) gitFs.getRelativePath().toAbsolutePath().getRoot();
        final RevTree root = rootPath.getRevTree();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
          treeWalk.addTree(root);
          final PathFilter filter = PathFilter.create("dir");
          treeWalk.setFilter(filter);
          treeWalk.setRecursive(false);
          final boolean foundDir = treeWalk.next();
          assertTrue(foundDir);
        }
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
          treeWalk.addTree(root);
          final PathFilter filter = PathFilter.create("dir/file.txt");
          treeWalk.setFilter(filter);
          treeWalk.setRecursive(false);
          final boolean foundDir = treeWalk.next();
          assertTrue(foundDir);
        }
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
          treeWalk.addTree(root);
          final PathFilter filter = PathFilter.create("dir");
          treeWalk.setFilter(filter);
          treeWalk.setRecursive(false);
          final boolean foundDir = treeWalk.next();
          assertTrue(foundDir);
          treeWalk.enterSubtree();
          assertTrue(treeWalk.next());
        }
      }
    }
  }

  @Test
  void testAttributes() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repo);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        assertThrows(NoSuchFileException.class,
            () -> ((GitAbsolutePath) gitFs.getAbsolutePath(commits.get(0), "/ploum.txt"))
                .readAttributes(ImmutableSet.of()));
      }
    }
  }

  @Test
  void testRealPath() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithLink(repo);
      assertEquals(4, commits.size());
      final ObjectId commit1 = commits.get(0);
      final ObjectId commit2 = commits.get(1);
      final ObjectId commit3 = commits.get(2);
      final ObjectId commit4 = commits.get(3);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        assertEquals(gitFs.getRelativePath().toAbsolutePath(),
            gitFs.getRelativePath().toRealPath(LinkOption.NOFOLLOW_LINKS));
        assertEquals(gitFs.getRelativePath().toAbsolutePath(),
            gitFs.getRelativePath().toRealPath());

        final GitPath c1File1 = gitFs.getPathRoot(commit1).resolve("file1.txt");
        assertEquals(c1File1, c1File1.toRealPath(LinkOption.NOFOLLOW_LINKS));
        assertEquals(c1File1, c1File1.toRealPath());

        assertEquals(c1File1, gitFs.getPathRoot(commit1).resolve("link.txt").toRealPath());
        assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getPathRoot(commit1)
            .resolve("link.txt").toRealPath(LinkOption.NOFOLLOW_LINKS));

        final GitPath c2File1 = gitFs.getPathRoot(commit2).resolve("file1.txt");
        assertEquals(c2File1, gitFs.getPathRoot(commit2).resolve("link.txt").toRealPath());
        assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getPathRoot(commit2)
            .resolve("link.txt").toRealPath(LinkOption.NOFOLLOW_LINKS));

        assertThrows(NoSuchFileException.class,
            () -> gitFs.getPathRoot(commit2).resolve("notexists.txt").toRealPath());
        assertThrows(NoSuchFileException.class, () -> gitFs.getPathRoot(commit2)
            .resolve("notexists.txt").toRealPath(LinkOption.NOFOLLOW_LINKS));

        final GitPath c3File1 = gitFs.getPathRoot(commit3).resolve("file1.txt");
        assertEquals(c3File1, gitFs.getPathRoot(commit3).resolve("dir/link").toRealPath());
        assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getPathRoot(commit3)
            .resolve("dir/link").toRealPath(LinkOption.NOFOLLOW_LINKS));

        assertEquals(c3File1,
            gitFs.getPathRoot(commit3).resolve("dir/linkToParent/dir/link").toRealPath());
        assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getPathRoot(commit3)
            .resolve("dir/linkToParent/dir/link").toRealPath(LinkOption.NOFOLLOW_LINKS));

        assertThrows(NoSuchFileException.class, () -> gitFs.getPathRoot(commit3)
            .resolve("dir/linkToParent/dir/notexists").toRealPath());
        assertThrows(NoSuchFileException.class, () -> gitFs.getPathRoot(commit3)
            .resolve("dir/notexists").toRealPath(LinkOption.NOFOLLOW_LINKS));
        assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getPathRoot(commit3)
            .resolve("dir/linkToParent/dir/notexists").toRealPath(LinkOption.NOFOLLOW_LINKS));

        assertThrows(NoSuchFileException.class,
            () -> gitFs.getPathRoot(commit3).resolve("dir/cyclingLink").toRealPath());
        assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getPathRoot(commit3)
            .resolve("dir/cyclingLink").toRealPath(LinkOption.NOFOLLOW_LINKS));

        assertThrows(NoSuchFileException.class,
            () -> gitFs.getPathRoot(commit4).resolve("file1.txt").toRealPath());
        assertThrows(NoSuchFileException.class, () -> gitFs.getPathRoot(commit4)
            .resolve("file1.txt").toRealPath(LinkOption.NOFOLLOW_LINKS));

        assertThrows(NoSuchFileException.class,
            () -> gitFs.getPathRoot(commit4).resolve("link.txt").toRealPath());
        assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getPathRoot(commit4)
            .resolve("link.txt").toRealPath(LinkOption.NOFOLLOW_LINKS));

        assertThrows(NoSuchFileException.class,
            () -> gitFs.getPathRoot(commit4).resolve("dir/linkToParent/dir/link").toRealPath());
        assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getPathRoot(commit4)
            .resolve("dir/linkToParent/dir/link").toRealPath(LinkOption.NOFOLLOW_LINKS));

        assertNotEquals(c1File1,
            gitFs.getPathRoot(commit3).resolve("././dir/../link.txt").toRealPath());
        assertEquals(c3File1,
            gitFs.getPathRoot(commit3).resolve("././dir/../link.txt").toRealPath());
        assertEquals(c3File1, gitFs.getPathRoot(commit3).resolve("././dir/../file1.txt")
            .toRealPath(LinkOption.NOFOLLOW_LINKS));
        assertEquals(c3File1, gitFs.getPathRoot(commit3).resolve("./link.txt").toRealPath());

        assertThrows(NoSuchFileException.class, () -> gitFs.getPathRoot(commit3)
            .resolve("dir/./../dir/./linkToParent/dir/notexists").toRealPath());
        assertThrows(NoSuchFileException.class, () -> gitFs.getPathRoot(commit3)
            .resolve("dir/./notexists").toRealPath(LinkOption.NOFOLLOW_LINKS));
        assertThrows(PathCouldNotBeFoundException.class,
            () -> gitFs.getPathRoot(commit3).resolve("dir/linkToParent/./dir/../dir/notexists")
                .toRealPath(LinkOption.NOFOLLOW_LINKS));

        assertThrows(NoSuchFileException.class,
            () -> gitFs.getPathRoot(commit3).resolve("dir/./../dir/cyclingLink").toRealPath());
        assertThrows(PathCouldNotBeFoundException.class, () -> gitFs.getPathRoot(commit3)
            .resolve("dir/./../dir/cyclingLink").toRealPath(LinkOption.NOFOLLOW_LINKS));
      }
    }
  }

  /**
   * This does not access the file system, in fact. But it does use the GFS path creation methods,
   * so I leave it here for now.
   */
  @Test
  void testStartsWith() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      JGit.createRepoWithSubDir(repo);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        assertTrue(gitFs.getRelativePath().toAbsolutePath()
            .startsWith(gitFs.getRelativePath().toAbsolutePath()));
        assertTrue(gitFs.getRelativePath("ploum.txt").toAbsolutePath()
            .startsWith(gitFs.getRelativePath().toAbsolutePath()));
        assertTrue(gitFs.getRelativePath("dir", "ploum.txt").toAbsolutePath()
            .startsWith(gitFs.getRelativePath().toAbsolutePath()));
        assertTrue(gitFs.getRelativePath("dir", "ploum.txt").toAbsolutePath()
            .startsWith(gitFs.getRelativePath("dir").toAbsolutePath()));
        assertFalse(gitFs.getRelativePath("dir", "ploum.txt").toAbsolutePath()
            .startsWith(gitFs.getRelativePath("dir", "p").toAbsolutePath()));
        assertFalse(gitFs.getRelativePath("dir", "ploum.txt").toAbsolutePath()
            .startsWith(gitFs.getRelativePath("dir", "plom.txt").toAbsolutePath()));
        assertFalse(gitFs.getRelativePath("dir", "ploum.txt").toAbsolutePath()
            .startsWith(gitFs.getRelativePath("dir", "ploum.txt")));
        assertFalse(gitFs.getRelativePath().toAbsolutePath().startsWith(gitFs.getRelativePath()));

        assertTrue(gitFs.getRelativePath("dir").startsWith(gitFs.getRelativePath("dir")));
        assertTrue(
            gitFs.getRelativePath("dir", "ploum.txt").startsWith(gitFs.getRelativePath("dir")));
        assertTrue(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
            .startsWith(gitFs.getRelativePath("dir")));
        assertTrue(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
            .startsWith(gitFs.getRelativePath("dir", "subdir")));
        assertFalse(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
            .startsWith(gitFs.getRelativePath("dir", "subdir", "p")));
        assertFalse(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
            .startsWith(gitFs.getRelativePath("dir", "subdir", "ploum.txt2")));
        assertFalse(gitFs.getRelativePath("dir", "subdir", "ploum.txt")
            .startsWith(gitFs.getRelativePath("subdir", "ploum.txt")));
        assertFalse(
            gitFs.getRelativePath("dir").startsWith(gitFs.getRelativePath().toAbsolutePath()));
        assertFalse(gitFs.getRelativePath().startsWith(gitFs.getRelativePath().toAbsolutePath()));
        assertTrue(gitFs.getRelativePath().startsWith(gitFs.getRelativePath()));

        assertThrows(InvalidPathException.class,
            () -> gitFs.getRelativePath().toAbsolutePath().startsWith("/refs"));
        assertThrows(InvalidPathException.class,
            () -> gitFs.getRelativePath().toAbsolutePath().startsWith("/refs//"));
        assertFalse(gitFs.getRelativePath().toAbsolutePath().startsWith("/refs/heads/"));
        assertTrue(gitFs.getRelativePath().toAbsolutePath().startsWith("/refs/heads/main/"));
      }
    }
  }

  @Test
  void testParentCommits() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithLink(repo);
      assertEquals(4, commits.size());
      final ObjectId commit1 = commits.get(0);
      final ObjectId commit2 = commits.get(1);
      final ObjectId commit3 = commits.get(2);
      final ObjectId commit4 = commits.get(3);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        final GitPathRootSha p1 = gitFs.getPathRoot(commit1);
        final GitPathRootSha p2 = gitFs.getPathRoot(commit2);
        final GitPathRootSha p3 = gitFs.getPathRoot(commit3);
        final GitPathRootSha p4 = gitFs.getPathRoot(commit4);
        assertEquals(ImmutableList.of(), p1.getParentCommits());
        assertEquals(ImmutableList.of(p1), p2.getParentCommits());
        assertEquals(GitPathRootShaImpl.class,
            Iterables.getOnlyElement(p2.getParentCommits()).getClass());
        assertEquals(ImmutableList.of(p2), p3.getParentCommits());
        assertEquals(ImmutableList.of(p3), p4.getParentCommits());
      }
    }
  }

  @Test
  void testParentCommitsGraphFirst() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithLink(repo);
      assertEquals(4, commits.size());
      final ObjectId commit1 = commits.get(0);
      final ObjectId commit2 = commits.get(1);
      final ObjectId commit3 = commits.get(2);
      final ObjectId commit4 = commits.get(3);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        gitFs.graph();
        final GitPathRootSha p1 = gitFs.getPathRoot(commit1);
        final GitPathRootSha p2 = gitFs.getPathRoot(commit2);
        final GitPathRootSha p3 = gitFs.getPathRoot(commit3);
        final GitPathRootSha p4 = gitFs.getPathRoot(commit4);
        assertEquals(ImmutableList.of(), p1.getParentCommits());
        assertEquals(ImmutableList.of(p1), p2.getParentCommits());
        assertEquals(GitPathRootShaCachedImpl.class,
            Iterables.getOnlyElement(p2.getParentCommits()).getClass());
        assertEquals(ImmutableList.of(p2), p3.getParentCommits());
        assertEquals(ImmutableList.of(p3), p4.getParentCommits());
      }
    }
  }

  @Test
  void testCaching() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithLink(repo);
      assertEquals(4, commits.size());
      final ObjectId commit1 = commits.get(0);
      final ObjectId commit2 = commits.get(1);
      final ObjectId commit3 = commits.get(2);
      final ObjectId commit4 = commits.get(3);
      try (GitFileSystem fs =
          GitFileSystemProvider.instance().newFileSystemFromDfsRepository(repo)) {
        final GitPathRootSha p1 = fs.getPathRoot(commit1);
        final GitPathRootSha p2 = fs.getPathRoot(commit2);
        final GitPathRootSha p3 = fs.getPathRoot(commit3);
        final GitPathRootSha p4 = fs.getPathRoot(commit4);
        final GitPathRootShaCached p1cached = p1.toShaCached();
        final GitPathRootShaCached p2cached = p2.toShaCached();
        final GitPathRootShaCached p3cached = p3.toShaCached();
        final GitPathRootShaCached p4cached = p4.toShaCached();
        assertEquals(p1cached, p1);
        assertEquals(p2cached, p2);
        assertEquals(p3cached, p3);
        assertEquals(p4cached, p4);
        assertEquals(commit1, p1cached.getCommit().id());
        assertEquals(commit2, p2cached.getCommit().id());
        assertEquals(commit3, p3cached.getCommit().id());
        assertEquals(commit4, p4cached.getCommit().id());
        assertEquals(commit1, p1.getCommit().id());
      }
    }
  }

  @Test
  void testGraph() throws Exception {
    try (
        DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createBasicRepo(repository);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repository)) {
        final Function<? super ObjectId, GitPathRootSha> oToP = gitFs::getPathRoot;
        LOGGER.debug("Commits: {}.", commits);
        assertEquals(toGraph(commits, oToP), gitFs.graph());
      }
    }
    try (
        DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithSubDir(repository);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repository)) {
        final Function<? super ObjectId, GitPathRootSha> oToP = gitFs::getPathRoot;
        assertEquals(toGraph(commits, oToP), gitFs.graph());
      }
    }
  }

  @Test
  void testGraphReading() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      final ImmutableList<ObjectId> commits = JGit.createRepoWithLink(repo);
      assertEquals(4, commits.size());
      try (GitFileSystem fs =
          GitFileSystemProvider.instance().newFileSystemFromDfsRepository(repo)) {

        final Set<GitPathRootShaCached> nodes = fs.graph().nodes();
        final ImmutableSet<ObjectId> graphIds =
            nodes.stream().map(p -> p.getCommit().id()).collect(ImmutableSet.toImmutableSet());
        assertEquals(ImmutableSet.copyOf(commits), graphIds);
      }
    }
  }

  private MutableGraph<GitPathRootSha> toGraph(ImmutableList<ObjectId> commits,
      Function<? super ObjectId, GitPathRootSha> oidToP) {
    return GraphUtils.transformed(GraphUtils.asGraph(commits),
        Maps.asMap(ImmutableSet.copyOf(commits), oidToP));
  }

  @Test
  void testRefs() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      JGit.createBasicRepo(repo);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        final ImmutableSet<? extends GitPathRootRef> refPaths = gitFs.refs();
        assertEquals(1, refPaths.size());
        assertEquals("refs/heads/main", Iterables.getOnlyElement(refPaths).getGitRef());
      }
    }
  }

  @Test
  void testFind() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      JGit.createRepoWithSubDir(repo);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        assertEquals(
            ImmutableSet.of(gitFs.getRelativePath(), gitFs.getRelativePath("file1.txt"),
                gitFs.getRelativePath("file2.txt"), gitFs.getRelativePath("dir"),
                gitFs.getRelativePath("dir", "file.txt")),
            Files.find(gitFs.getRelativePath(), 4, (p, a) -> true)
                .collect(ImmutableSet.toImmutableSet()));
        assertEquals(
            ImmutableSet.of(gitFs.getRelativePath("dir").toAbsolutePath(),
                gitFs.getRelativePath("dir", "file.txt").toAbsolutePath()),
            Files.find(gitFs.getRelativePath("dir").toAbsolutePath(), 4, (p, a) -> true)
                .collect(ImmutableSet.toImmutableSet()));
      }
    }
  }

  @Test
  void testReadDir() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      JGit.createBasicRepo(repo);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        assertThrows(NoSuchFileException.class,
            () -> Files.newDirectoryStream(gitFs.getRelativePath("no such dir").toAbsolutePath(),
                (p) -> true));
        assertEquals(
            ImmutableSet.of(gitFs.getRelativePath("file1.txt"),
                gitFs.getRelativePath("", "file2.txt")),
            ImmutableSet.copyOf(Files.newDirectoryStream(gitFs.getRelativePath(), p -> true)));
        assertEquals(
            ImmutableSet.of(gitFs.getRelativePath("file1.txt").toAbsolutePath(),
                gitFs.getRelativePath("file2.txt").toAbsolutePath()),
            ImmutableSet.copyOf(
                Files.newDirectoryStream(gitFs.getRelativePath().toAbsolutePath(), p -> true)));

        assertThrows(NotDirectoryException.class, () -> Files
            .newDirectoryStream(gitFs.getRelativePath("file1.txt").toAbsolutePath(), p -> true));
      }
    }
  }

  @Test
  void testReadSubDir() throws Exception {
    try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
      JGit.createRepoWithSubDir(repo);
      try (GitFileSystem gitFs =
          GitFileSystemProviderImpl.getInstance().newFileSystemFromDfsRepository(repo)) {
        final ImmutableSet<GitPath> subEntries = ImmutableSet.of(gitFs.getRelativePath("dir"),
            gitFs.getRelativePath("file1.txt"), gitFs.getRelativePath("file2.txt"));
        final ImmutableSet<GitPath> subEntriesAbsolute =
            ImmutableSet.of(gitFs.getRelativePath("dir").toAbsolutePath(),
                gitFs.getRelativePath("file1.txt").toAbsolutePath(),
                gitFs.getRelativePath("file2.txt").toAbsolutePath());
        final GitPathImpl relativePath = (GitPathImpl) gitFs.getRelativePath();
        assertEquals(subEntries, ImmutableSet.copyOf(relativePath.newDirectoryStream(p -> true)));
        assertEquals(subEntries,
            Files.list(gitFs.getRelativePath()).collect(ImmutableSet.toImmutableSet()));
        final GitPathImpl absolutePath = (GitPathImpl) gitFs.getRelativePath().toAbsolutePath();
        assertEquals(subEntriesAbsolute,
            ImmutableSet.copyOf(absolutePath.newDirectoryStream(p -> true)));
        assertEquals(subEntriesAbsolute, Files.list(gitFs.getRelativePath().toAbsolutePath())
            .collect(ImmutableSet.toImmutableSet()));
      }
    }
  }

  @Test
  void testFindSmall() throws Exception {
    final Path repoWorkDir = Files.createTempDirectory("my-small-repo");
    try (Repository repository =
        new FileRepositoryBuilder().setWorkTree(repoWorkDir.toFile()).build()) {
      JGit.createBasicRepo(repository);
    }

    try (
        Repository repository =
            new FileRepositoryBuilder().setWorkTree(repoWorkDir.toFile()).build();
        GitFileSystem gitFs =
            GitFileSystemProviderImpl.getInstance().newFileSystemFromRepository(repository)) {
      search(repository, gitFs);
      search(repository, gitFs);
      search(repository, gitFs);
    }
  }

  private void search(Repository repository, GitFileSystem gitFs)
      throws IOException, NoSuchFileException, MissingObjectException, IncorrectObjectTypeException,
      CorruptObjectException {
    final String searched = "non-existing.png";

    final GitPathRoot main = gitFs.getPathRoot("/refs/heads/main/");
    final GitPathRoot relevantBranch =
        Files.exists(main) ? main : gitFs.getPathRoot("/refs/heads/master/");
    verify(Files.exists(relevantBranch));
    final ObjectId id = relevantBranch.getCommit().id();
    final GitPathRoot pathId = gitFs.getPathRoot(id);

    LOGGER.info("Searching for file directly.");
    try (ObjectReader reader = repository.newObjectReader(); RevWalk walker = new RevWalk(reader);
        TreeWalk treeWalk = new TreeWalk(reader);) {
      final RevCommit commit = walker.parseCommit(id);
      final RevTree commitTree = commit.getTree();
      treeWalk.addTree(commitTree);
      treeWalk.setRecursive(true);
      while (treeWalk.next()) {
        if (treeWalk.getNameString().equals(searched)) {
          LOGGER.info("Found: {}.", treeWalk.getPathString());
        }
      }
    }
    LOGGER.info("Searched everywhere.");

    LOGGER.info("Searching for file indirectly.");
    try (ObjectReader reader = repository.newObjectReader(); RevWalk walker = new RevWalk(reader);
        TreeWalk treeWalk = new TreeWalk(reader);) {
      final RevCommit commit = walker.parseCommit(id);
      final RevTree commitTree = commit.getTree();
      treeWalk.addTree(commitTree);
      treeWalk.setRecursive(false);
      try (GitFileSystemImpl.TreeWalkDirectoryStream dirWalker =
          new GitFileSystemImpl.TreeWalkDirectoryStream(treeWalk)) {
        final PeekingIterator<GitStringObject> iterator = dirWalker.iterator();
        while (iterator.hasNext()) {
          final GitStringObject obj = iterator.next();
          if (obj.getFileName().toString().equals(searched)) {
            LOGGER.info("Found: indirectly.");
          }
        }
      }
    }
    LOGGER.info("Searched everywhere.");

    {
      final ImmutableSet<Path> paths;
      LOGGER.info("Searching for file through gitFs.");
      try (Stream<Path> found = Files.find(pathId, 100, (p, a) -> false)) {
        // (p, a) -> p.getFileName() != null && p.getFileName().toString().equals(searched))) {
        paths = found.collect(ImmutableSet.toImmutableSet());
      }
      LOGGER.info("Found: {}.", paths);
      assertEquals(0, paths.size());
    }
  }

  @Test
  void testFindBig() throws Exception {
    final Path repoWorkDir = Files.createTempDirectory("minimax-ex");
    // Files.delete(repoWorkDir);
    final GitUri uri = RepositoryCoordinates
        .from("git@github.com", "oliviercailloux-org", "minimax-ex").asGitUri();
    LOGGER.info("From {}.", uri.asString());
    try (FileRepository repository = download(uri, repoWorkDir); GitFileSystem gitFs =
        GitFileSystemProviderImpl.getInstance().newFileSystemFromRepository(repository)) {
      search(repository, gitFs);
      search(repository, gitFs);
      search(repository, gitFs);
    }
  }
}
