package io.github.oliviercailloux.gitjfs;

import io.github.oliviercailloux.gitjfs.impl.GitPathRootImpl;
import java.io.IOException;
import java.nio.file.NoSuchFileException;

/**
 * A {@link GitPathRootImpl} containing a git ref.
 */
public interface GitPathRootRef extends GitPathRoot {

	@Override
	GitPathRootSha toSha() throws IOException, NoSuchFileException;

}
