package io.github.oliviercailloux.gitjfs;

import java.io.IOException;

public interface GitPathRootShaCached extends GitPathRootSha {

	@Override
	Commit getCommit();

	@Override
	GitPathRootShaCachedImpl toSha();

	@Override
	GitPathRootShaCachedImpl toShaCached() throws IOException;

}
