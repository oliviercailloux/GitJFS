package io.github.oliviercailloux.gitjfs;

public interface GitPathRootShaCached extends GitPathRootSha {

	@Override
	Commit getCommit();

	@Override
	GitPathRootShaCached toShaCached();

}
