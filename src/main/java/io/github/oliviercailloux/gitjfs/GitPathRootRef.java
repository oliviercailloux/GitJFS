package io.github.oliviercailloux.gitjfs;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

/**
 *
 * A {@link GitPathRootImpl} containing a git ref.
 *
 */
public interface GitPathRootRef extends GitPathRoot {

	@Override
	GitPathRootShaImpl toSha() throws IOException, NoSuchFileException;

}
