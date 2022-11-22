package io.github.oliviercailloux.gitjfs;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

public interface GitPathRootSha extends GitPathRoot {

	@Override
	GitPathRootSha toSha();

	@Override
	GitPathRootShaCached toShaCached() throws IOException, NoSuchFileException;

}
