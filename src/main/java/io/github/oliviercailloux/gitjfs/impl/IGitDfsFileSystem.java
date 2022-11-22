package io.github.oliviercailloux.gitjfs.impl;

import io.github.oliviercailloux.gitjfs.IGitFileSystem;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;

/**
 * A git file system that rests on a (user-managed) {@link DfsRepository}.
 *
 * @see GitFileSystemProviderImpl#newFileSystemFromDfsRepository(DfsRepository)
 */
public interface IGitDfsFileSystem extends IGitFileSystem {
	DfsRepository getRepository();
}
