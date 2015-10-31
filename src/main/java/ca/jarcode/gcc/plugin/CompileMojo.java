package ca.jarcode.gcc.plugin;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Mojo(name = "compile")
@SuppressWarnings("unused")
public class CompileMojo extends AbstractMojo {

	@Parameter(defaultValue = "src/main/c")
	private File sourceFolder;

	@Parameter(defaultValue = "*")
	private String compiler;

	@Parameter(defaultValue = "target/objects")
	private File objectsTargetFolder;

	@Parameter(defaultValue = "")
	private String arguments;

	@Parameter(defaultValue = "-o")
	private String outputFlag;

	@Parameter(defaultValue = "-c")
	private String compileOnlyFlag;

	@Parameter(defaultValue = "${project.basedir}")
	private File workingDirectory;

	@Parameter(defaultValue = "native")
	private String targetPlatforms;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		CompilerTools.handleTargets(targetPlatforms, this::execute, getLog());
	}

	public void execute(CompileTarget target) throws MojoExecutionException, MojoFailureException {
		getLog().info("Linking native target: " + target);

		File processedTargetFolder = new File(objectsTargetFolder, target.name());

		String processedCompiler = compiler;
		String processedArguments = arguments;

		if (compiler.equals("*")) {
			processedCompiler = CompilerTools.compiler(target);
		}

		String[] extra = CompilerTools.extraFlags(target);

		processedArguments += extra == null ? "" : " " + Arrays.asList(extra).stream().collect(Collectors.joining(" "));

		// <Mapping, File>
		HashMap<String, File> sourceFiles = new HashMap<>();
		CompilerTools.forkFind(sourceFolder, sourceFiles, false);

		for (Map.Entry<String, File> entry : sourceFiles.entrySet()) {

			int ext = -1;
			for (String suffix : CompilerTools.EXTENSIONS) {
				if (entry.getKey().endsWith("." + suffix)) {
					ext = suffix.length() + 1;
					break;
				}
			}

			if (ext == -1)
				continue;

			String obj = entry.getKey().substring(0, entry.getKey().length() - ext) + ".o";

			String targetFolder = processedTargetFolder.getAbsolutePath();
			if (!targetFolder.trim().endsWith(File.separator))
				targetFolder += File.separator;

			File targetFile = new File(targetFolder + obj);

			if (!targetFile.getParentFile().exists() && !targetFile.getParentFile().mkdirs()) {
				getLog().error("Failed to make directory (or parent directories): " +
						targetFile.getParentFile().getAbsolutePath());
				throw new MojoFailureException("compile mojo failed");
			}

			int result = CompilerTools.EXECUTOR.exec(processedCompiler + "\0" + compileOnlyFlag + "\0" +
					processedArguments.replace(" ", "\0") + "\0" + entry.getValue().getAbsolutePath() + "\0"
					+ outputFlag + (target == CompileTarget.OSX ? " " : "") + // yes, there is a space only for OSX.
					targetFolder + obj, getLog(), workingDirectory);

			if (result != 0) {
				getLog().error(String.format("Failed to compile source file (exit code %d)", result));
				throw new MojoFailureException("compile mojo failed");
			}
		}
	}
}
