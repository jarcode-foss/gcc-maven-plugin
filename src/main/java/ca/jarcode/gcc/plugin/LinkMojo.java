package ca.jarcode.gcc.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Mojo(name = "link")
@SuppressWarnings("unused")
public class LinkMojo extends AbstractMojo {

	@Parameter(defaultValue = "target/objects")
	private File objectsTargetFolder;

	@Parameter(defaultValue = "target/natives")
	private File nativesTargetFolder;

	@Parameter(defaultValue = "")
	private String arguments;

	@Parameter(defaultValue = "*")
	private String linker;

	@Parameter(defaultValue = "-o")
	private String outputFlag;

	@Parameter(defaultValue = "*")
	private String libraryFlag;

	@Parameter(defaultValue = "false")
	private boolean library;

	@Parameter
	private String targetName;

	@Parameter(defaultValue = "${project.basedir}")
	private File workingDirectory;

	@Parameter(defaultValue = "false")
	private boolean useAbsoluteNames;

	@Parameter(defaultValue = "native")
	private String targetPlatforms;

	@Parameter(defaultValue = "true")
	private boolean buildImportLibraries;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		CompilerTools.handleTargets(targetPlatforms, this::execute, getLog());
	}

	public void execute(CompileTarget target) throws MojoExecutionException, MojoFailureException {
		getLog().info("Compiling native target: " + target);

		objectsTargetFolder = new File(objectsTargetFolder, target.name());

		String unformattedTargetName = targetName;
		String processedTargetName = targetName;
		String processedLibraryFlag = libraryFlag;
		String processedLinker = linker;
		StringBuilder argumentsBuilder = new StringBuilder();

		argumentsBuilder.append(arguments);

		if (!useAbsoluteNames) {
			processedTargetName = (library) ?
					CompilerTools.libraryFormat(target).apply(targetName) :
					CompilerTools.executableFormat(target).apply(targetName);
		}

		if (libraryFlag.equals("*")) {
			processedLibraryFlag = CompilerTools.libFlag(target);
		}

		if (linker.equals("*")) {
			processedLinker = CompilerTools.compiler(target);
		}

		String[] extra = CompilerTools.extraFlags(target);

		Consumer<String[]> append = (arr) ->
				argumentsBuilder.append(
						arr == null ? "" : " " + Arrays.asList(arr).stream().collect(Collectors.joining(" ")
				));

		append.accept(extra);

		if (buildImportLibraries) {
			extra = CompilerTools.importLibraryFlags(target, unformattedTargetName);
			append.accept(extra);
		}

		HashMap<String, File> objectFiles = new HashMap<>();

		CompilerTools.forkFind(objectsTargetFolder, objectFiles, true);

		String objects = objectFiles.entrySet().stream()
				.map((entry) -> entry.getValue().getAbsolutePath())
				.collect(Collectors.joining("\0"));

		String targetFolderPrefix = nativesTargetFolder.getAbsolutePath();
		if (!targetFolderPrefix.trim().endsWith(File.separator))
			targetFolderPrefix += File.separator;

		File targetFile = new File(targetFolderPrefix + processedTargetName);

		if (!targetFile.getParentFile().exists() && !targetFile.getParentFile().mkdirs()) {
			getLog().error("Failed to make directory (or parent directories): " +
					targetFile.getParentFile().getAbsolutePath());
			throw new MojoFailureException("linking mojo failed");
		}

		int result = CompilerTools.EXECUTOR.exec(processedLinker + "\0" + objects + "\0" +
				argumentsBuilder.toString().replace(" ", "\0") + "\0" + (library ? (processedLibraryFlag + "\0") : "")
				+ outputFlag + (target == CompileTarget.OSX ? " " : "") + // yes, there is a space only for OSX.
				targetFolderPrefix + processedTargetName, getLog(), workingDirectory);

		if (result != 0) {
			getLog().error(String.format("Failed to link object files (exit code %d)", result));
			throw new MojoFailureException("linking mojo failed");
		}
	}
}
