package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.URI_UNCHECKER;

import com.google.common.base.VerifyException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.github.oliviercailloux.gitjfs.GitDfsFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import java.net.URI;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GitFileSystems {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystems.class);

  private static final String FILE_AUTHORITY = "FILE";
  private static final String DFS_AUTHORITY = "DFS";

  /**
   * Keys are absolute paths.
   */
  private final BiMap<Path, GitFileFileSystemImpl> cachedFileFileSystems = HashBiMap.create();
  /**
   * Key is the repository name, unescaped (original). An empty name is authorized (this is sensible
   * when the user wishes to have only one in-memory file system).
   */
  private final BiMap<String, GitDfsFileSystemImpl> cachedDfsFileSystems = HashBiMap.create();

  public void verifyCanCreateFileSystemCorrespondingTo(Path gitDir)
      throws FileSystemAlreadyExistsException {
    if (cachedFileFileSystems.containsKey(gitDir.toAbsolutePath())) {
      throw new FileSystemAlreadyExistsException();
    }
  }

  public void verifyCanCreateFileSystemCorrespondingTo(DfsRepository dfsRepository)
      throws FileSystemAlreadyExistsException {
    final String name = getName(dfsRepository);
    if (cachedDfsFileSystems.containsKey(name)) {
      throw new FileSystemAlreadyExistsException(
          "A repository with the name ‘" + name + "’ already exists.");
    }
  }

  String getExistingUniqueId(GitFileSystem gitFs) {
    /* FIXME */
    final BiMap<?, ? extends GitFileSystem> cachedSystems = cachedSystems(gitFs);

    @SuppressWarnings("unlikely-arg-type")
    final Object key = cachedSystems.inverse().get(gitFs);
    checkArgument(key != null);
    return key.toString();
  }

  /**
   * @param gitFileUri must parse as a git file-based filesystem URI.
   * @return the absolute path in the URI, representing the git directory that this URI refers to.
   */
  public Path getGitDir(URI gitFileUri) {
    checkArgument(Objects.equals(gitFileUri.getScheme(), GitFileSystemProviderImpl.SCHEME));
    checkArgument(Objects.equals(gitFileUri.getAuthority(), FILE_AUTHORITY),
        "Unexpected authority: " + gitFileUri.getAuthority() + ", expected " + FILE_AUTHORITY
            + ".");
    /**
     * It follows from these two checks that the uri is absolute (it has a scheme) and hierarchical
     * (it was further parsed).
     */
    verify(gitFileUri.isAbsolute());
    verify(!gitFileUri.isOpaque());

    final String gitDirStr = gitFileUri.getPath();
    /** An hierarchical absolute URI has an absolute path. */
    verifyNotNull(gitDirStr);
    checkArgument(gitDirStr.endsWith("/"));
    final Path gitDir = Path.of(gitDirStr);
    verify(gitDir.isAbsolute());
    return gitDir;
  }

  private String getRepositoryName(URI gitDfsUri) {
    checkArgument(Objects.equals(gitDfsUri.getScheme(), GitFileSystemProviderImpl.SCHEME));
    checkArgument(Objects.equals(gitDfsUri.getAuthority(), DFS_AUTHORITY));
    final String path = gitDfsUri.getPath();
    verify(path.startsWith("/"));
    final String name = path.substring(1);
    return name;
  }

  @SuppressWarnings("resource")
  public GitFileSystem getFileSystem(URI gitUri) throws FileSystemNotFoundException {
    checkArgument(Objects.equals(gitUri.getScheme(), GitFileSystemProviderImpl.SCHEME));
    final String authority = gitUri.getAuthority();
    checkArgument(authority != null);
    final GitFileSystem fs;
    switch (authority) {
      case FILE_AUTHORITY:
        final Path gitDir = getGitDir(gitUri);
        fs = getFileSystemFromGitDir(gitDir);
        break;
      case DFS_AUTHORITY:
        final String name = getRepositoryName(gitUri);
        fs = getFileSystemFromName(name);
        break;
      default:
        throw new VerifyException();
    }
    return fs;
  }

  @SuppressWarnings("resource")
  public GitFileSystemImpl getFileSystemDelegate(URI gitUri) throws FileSystemNotFoundException {
    checkArgument(Objects.equals(gitUri.getScheme(), GitFileSystemProviderImpl.SCHEME));
    final String authority = gitUri.getAuthority();
    checkArgument(authority != null);
    final GitFileSystemImpl fs;
    switch (authority) {
      case FILE_AUTHORITY:
        final Path gitDir = getGitDir(gitUri);
        fs = getFileSystemFromGitDir(gitDir).delegate();
        break;
      case DFS_AUTHORITY:
        final String name = getRepositoryName(gitUri);
        fs = getFileSystemFromName(name).delegate();
        break;
      default:
        throw new VerifyException();
    }
    return fs;
  }

  public GitFileFileSystemImpl getFileSystemFromGitDir(Path gitDir)
      throws FileSystemNotFoundException {
    final Path absolutePath = gitDir.toAbsolutePath();
    if (!cachedFileFileSystems.containsKey(absolutePath)) {
      throw new FileSystemNotFoundException("No system at " + gitDir + ".");
    }
    return cachedFileFileSystems.get(absolutePath);
  }

  public GitDfsFileSystemImpl getFileSystemFromName(String name)
      throws FileSystemNotFoundException {
    if (!cachedDfsFileSystems.containsKey(name)) {
      throw new FileSystemNotFoundException("No system at " + name + ".");
    }
    return cachedDfsFileSystems.get(name);
  }

  public URI toUri(GitFileSystem gitFsOrDelegate) {
    final GitFileSystem gitFs;
    if ((gitFsOrDelegate instanceof GitFileFileSystem)
        || (gitFsOrDelegate instanceof GitDfsFileSystem)) {
      gitFs = gitFsOrDelegate;
    } else {
      gitFs = havingDelegate(gitFsOrDelegate);
    }
    if (gitFs instanceof GitFileFileSystem gitFileFs) {
      final Path gitDir = gitFileFs.getGitDir();
      final String pathStr = gitDir.toAbsolutePath().toString();
      final String pathStrSlash = pathStr.endsWith("/") ? pathStr : pathStr + "/";
      return URI_UNCHECKER.getUsing(
          () -> new URI(GitFileSystemProviderImpl.SCHEME, FILE_AUTHORITY, pathStrSlash, null));
    }

    if (gitFs instanceof GitDfsFileSystem gitDfsFs) {
      @SuppressWarnings("resource")
      final String name = getName(gitDfsFs.getRepository());
      verifyNotNull(name);
      /**
       * I’d like not to have the possible / in name to reach the URI and act as segment separator
       * in the URI path. But it might be that encoding it with %2F as usual will make it equivalent
       * to a /, at least, the URI class acts so. So I’d have to encode / to something else using a
       * home-grown encoding. Let’s not go that far. Also, it might be good anyway to not encode
       * slashes and thus treat them as segment separators: if the user used slashes in the
       * repository name, it might well be with the intent of separating segments.
       */
      return URI_UNCHECKER.getUsing(
          () -> new URI(GitFileSystemProviderImpl.SCHEME, DFS_AUTHORITY, "/" + name, null));
    }

    throw new IllegalArgumentException("Unknown repository type.");
  }

  private GitFileSystem havingDelegate(GitFileSystem delegate) {
    final ImmutableSet<GitFileFileSystemImpl> fileFses = cachedFileFileSystems.values().stream()
        .filter(v -> v.delegate().equals(delegate)).collect(ImmutableSet.toImmutableSet());
    final ImmutableSet<GitDfsFileSystemImpl> dfsFses = cachedDfsFileSystems.values().stream()
        .filter(v -> v.delegate().equals(delegate)).collect(ImmutableSet.toImmutableSet());
    final ImmutableSet<GitFileSystem> founds = Sets.union(fileFses, dfsFses).immutableCopy();
    verify(founds.size() <= 1);
    checkArgument(founds.size() == 1);
    return Iterables.getOnlyElement(founds);
  }

  private String getName(final DfsRepository dfsRepository) {
    return dfsRepository.getDescription().getRepositoryName();
  }

  public void put(Path gitDir, GitFileFileSystemImpl newFs) {
    LOGGER.debug("Adding an entry at {}: {}.", gitDir, newFs);
    verifyCanCreateFileSystemCorrespondingTo(gitDir);
    cachedFileFileSystems.put(gitDir.toAbsolutePath(), newFs);
  }

  @SuppressWarnings("resource")
  public void put(DfsRepository dfsRespository, GitDfsFileSystemImpl newFs) {
    verifyCanCreateFileSystemCorrespondingTo(dfsRespository);
    cachedDfsFileSystems.put(getName(dfsRespository), newFs);
  }

  void hasBeenClosedEvent(GitFileFileSystemImpl gitFs) {
    hasBeenClosedEventImpl(gitFs);
  }

  void hasBeenClosedEvent(GitDfsFileSystemImpl gitFs) {
    hasBeenClosedEventImpl(gitFs);
  }

  private void hasBeenClosedEventImpl(GitFileSystem gitFs) {
    final BiMap<?, ? extends GitFileSystem> cachedSystems = cachedSystems(gitFs);
    LOGGER.debug("Removing {} from {}.", gitFs, cachedSystems);
    final BiMap<? extends GitFileSystem, ?> inverse = cachedSystems.inverse();
    @SuppressWarnings("unlikely-arg-type")
    final Object key = inverse.remove(gitFs);
    checkArgument(key != null, inverse.keySet().toString());
  }

  private BiMap<?, ? extends GitFileSystem> cachedSystems(GitFileSystem gitFs) {
    final BiMap<?, ? extends GitFileSystem> cachedSystems;
    if (gitFs instanceof GitFileFileSystemImpl gitFileFs) {
      cachedSystems = cachedFileFileSystems;
    } else if (gitFs instanceof GitDfsFileSystemImpl gitDfsFs) {
      cachedSystems = cachedDfsFileSystems;
    } else {
      throw new IllegalArgumentException("Unknown repository type.");
    }
    return cachedSystems;
  }
}
