package io.github.oliviercailloux.gitjfs;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

public interface GitPathRootSha extends GitPathRoot {

	@Override
	GitPathRootShaImpl toSha();

	@Override
	GitPathRootShaCachedImpl toShaCached() throws IOException, NoSuchFileException;

}
