package ca.jarcode.gcc.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.HashMap;
import java.util.stream.Collectors;

@Mojo(name = "link")
public class LinkMojo extends AbstractMojo {

	@Parameter(defaultValue = "target/objects")
	private File objectsTargetFolder;

	@Parameter(defaultValue = "target/natives")
	private File nativesTargetFolder;

	@Parameter(defaultValue = "")
	private String arguments;

	@Parameter(defaultValue = "cc")
	private String linker;

	@Parameter(defaultValue = "-o")
	private String outputFlag;

	@Parameter(defaultValue = "-shared")
	private String libraryFlag;

	@Parameter(defaultValue = "false")
	private boolean library;

	@Parameter
	private String targetName;

	@Parameter(defaultValue = "${project.basedir}")
	private File workingDirectory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("Compiling .c and .cpp source files");

		HashMap<String, File> objectFiles = new HashMap<>();
		CompilerTools.forkFind(objectsTargetFolder, objectFiles, true);

		String objects = objectFiles.entrySet().stream()
				.map((entry) -> entry.getValue().getAbsolutePath())
				.collect(Collectors.joining("\0"));

		String targetFolderPrefix = nativesTargetFolder.getAbsolutePath();
		if (!targetFolderPrefix.trim().endsWith(File.separator))
			targetFolderPrefix += File.separator;

		File targetFile = new File(targetFolderPrefix + targetName);

		if (!targetFile.getParentFile().exists() && !targetFile.getParentFile().mkdirs()) {
			getLog().error("Failed to make directory (or parent directories): " +
					targetFile.getParentFile().getAbsolutePath());
			throw new MojoFailureException("linking mojo failed");
		}

		int result = CompilerTools.EXECUTOR.exec(linker + "\0" + objects + "\0" +
				arguments.replace(" ", "\0") + "\0" + (library ? (libraryFlag + "\0") : "") + outputFlag +
				targetFolderPrefix + targetName, getLog(), workingDirectory);

		if (result != 0) {
			getLog().error(String.format("Failed to link object files (exit code %d)", result));
			throw new MojoFailureException("linking mojo failed");
		}
	}
}
