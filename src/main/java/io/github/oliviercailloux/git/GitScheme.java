package io.github.oliviercailloux.git;

public enum GitScheme {
	FILE, GIT, HTTP, HTTPS, FTP, FTPS, SSH;

	/**
	 * Returns this scheme in lower case.
	 *
	 * @return a lower case string
	 */
	@Override
	public String toString() {
		return super.toString().toLowerCase();
	}
}
