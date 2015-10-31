package ca.jarcode.gcc.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class CompilerTools {

	// these are source file extensions used by this plugin
	public static final String[] EXTENSIONS = {
			"c", "cpp"
	};

	@FunctionalInterface
	interface ShellExecutor {
		int exec(String command, Log logger, File workingDirectory) throws MojoFailureException;
	}

	public static String compiler(CompileTarget target) {
		String os = System.getProperty("os.name");
		if (os.contains("Windows")) {
			switch (target) {
				case WIN32:
					return "gcc";
				case WIN64:
					return "gcc";
				case OSX:
					throw new UnsupportedOperationException("OSX-cross compiling not supported");
				case ELF32:
					throw new UnsupportedOperationException("Windows platforms cannot cross-compile");
				case ELF64:
					throw new UnsupportedOperationException("Windows platforms cannot cross-compile");
			}
		}
		else if (os.contains("Linux") || os.contains("FreeBSD") || os.contains("Android")) {
			switch (target) {
				case WIN32:
					return "i686-w64-mingw32-gcc";
				case WIN64:
					return "x86_64-w64-mingw32-gcc";
				case OSX:
					throw new UnsupportedOperationException("OSX-cross compiling not supported");
				case ELF32:
					return "gcc";
				case ELF64:
					return "gcc";
			}
		}
		else if (os.contains("Mac")) {
			switch (target) {
				case WIN32:
					throw new UnsupportedOperationException("OSX platforms cannot cross-compile");
				case WIN64:
					throw new UnsupportedOperationException("OSX platforms cannot cross-compile");
				case OSX:
					return "gcc";
				case ELF32:
					throw new UnsupportedOperationException("OSX platforms cannot cross-compile");
				case ELF64:
					throw new UnsupportedOperationException("OSX platforms cannot cross-compile");
			}
		}

		throw new RuntimeException("Unsupported platform");
	}

	public static String[] importLibraryFlags(CompileTarget target, String unformattedName) {
		switch (target) {
			case ELF32:
			case ELF64:
			case OSX: return null;
			case WIN32: return new String[] {"-Wl,--out-implib," + unformattedName + "32.a"};
			case WIN64: return new String[] {"-Wl,--out-implib," + unformattedName + ".a"};
			default: throw new RuntimeException("Unsupported platform");
		}
	}

	public static String[] extraFlags(CompileTarget target) {
		switch (target) {
			case ELF32:
			case WIN32: return new String[] {"-m32"};
			case WIN64:
			case ELF64: return new String[] {"-m64"};
			case OSX: return null;
			default: throw new RuntimeException("Unsupported platform");
		}
	}

	public static String libFlag(CompileTarget target) {
		switch (target) {
			case ELF32:
			case ELF64:
			case WIN32:
			case WIN64: return "-shared";
			case OSX: return "-dynamiclib";
			default: throw new RuntimeException("Unsupported platform");
		}
	}

	public static boolean arch32() {
		return System.getProperty("os.arch").contains("86") && System.getProperty("os.arch").contains("x64_86");
	}

	public static CompileTarget getNativeTarget() {
		String os = System.getProperty("os.name");
		boolean arch32 = arch32();
		if (os.contains("Linux") || os.contains("FreeBSD") || os.contains("Android"))
			return (arch32 ? CompileTarget.ELF32 : CompileTarget.ELF64);
		else if (os.contains("Windows"))
			return (arch32 ? CompileTarget.WIN32 : CompileTarget.WIN64);
		else if (os.contains("Mac"))
			return CompileTarget.OSX;
		else throw new RuntimeException("Unknown platform");
	};

	public static Function<String, String> libraryFormat(CompileTarget target) {
		switch (target) {
			case ELF32: return (lib) -> "lib" + lib + "32.so";
			case ELF64: return (lib) -> "lib" + lib + ".so";
			case WIN32: return (lib) -> lib + "32.dll";
			case WIN64: return (lib) -> lib + ".dll";
			case OSX: return (lib) -> lib + "dylib";
			default: throw new RuntimeException("Unsupported platform");
		}
	}

	public static Function<String, String> executableFormat(CompileTarget target) {
		switch (target) {
			case ELF32: return (lib) -> lib + "x86";
			case ELF64: return (lib) -> lib + "x86_64";
			case WIN32: return (lib) -> lib + "x86.exe";
			case WIN64: return (lib) -> lib + "x86_64.exe";
			case OSX: return (lib) -> lib;
			default: throw new RuntimeException("Unsupported platform");
		}
	}

	public static final ShellExecutor EXECUTOR = (cmd, logger, dir) -> {
		try {
			logger.info("");
			logger.info(cmd);
			logger.info("");

			final Process process = new ProcessBuilder()
					.command(cmd.split("\0"))
					.directory(dir)
					.start();

			AtomicReference<Throwable> errResult = new AtomicReference<>(null);
			AtomicReference<Throwable> outResult = new AtomicReference<>(null);

			Thread errStreamTask = new Thread(() ->
					CompilerTools.readProcessStream(process.getErrorStream(), logger::error, errResult));

			Thread outStreamTask = new Thread(() ->
					CompilerTools.readProcessStream(process.getInputStream(), logger::info, outResult));

			errStreamTask.start();
			outStreamTask.start();

			int result = process.waitFor();
			errStreamTask.join();
			outStreamTask.join();
			Throwable errThrowable = errResult.get();
			Throwable outThrowable = outResult.get();
			if (errThrowable != null)
				throw errThrowable;
			if (outThrowable != null)
				throw outThrowable;
			logger.info("");
			return result;
		}
		catch (Throwable e) {
			e.printStackTrace();
			throw new MojoFailureException("failed to run compile command");
		}
	};

	public static void readProcessStream(InputStream stream, Consumer<String> logFunction,
	                                     AtomicReference<Throwable> result) {
		try {
			InputStreamReader reader = new InputStreamReader(stream);
			StringBuilder builder = new StringBuilder();
			int in;
			while ((in = reader.read()) != -1) {
				char c = (char) in;
				if (c == '\n') {
					logFunction.accept(builder.toString());
					builder = new StringBuilder();
				}
				else builder.append(c);
			}
			if (builder.length() > 0) {
				logFunction.accept(builder.toString());
			}
		}
		catch (Throwable e) {
			result.set(e);
		}
	}

	public static void  forkFind(File f, HashMap<String, File> m, boolean objects) {
		String path = f.getAbsolutePath();
		if (!path.endsWith(File.separator))
			path += File.separator;
		forkFind(f, m, path.length(), objects);
	}

	public static void forkFind(File f, HashMap<String, File> m, int sub, boolean objects) {
		if (!f.isDirectory())
			return;
		File[] files = f.listFiles();
		if (files == null)
			return;
		for (File file : files) {

			boolean valid = false;
			for (String suffix : EXTENSIONS) {
				if (!objects) {
					if (file.getName().endsWith("." + suffix)) {
						valid = true;
						break;
					}
				}
				else if (file.getName().endsWith(".o")) {
					valid = true;
					break;
				}
			}

			if (valid) {
				String mapping = file.getAbsolutePath().substring(sub);
				m.put(mapping, file);
				forkFind(file, m, sub, objects);
			}
		}
	}

	@FunctionalInterface
	interface MojoExecution {
		void execute(CompileTarget target) throws MojoExecutionException, MojoFailureException;
	}

	public static void handleTargets(String targetPlatforms, MojoExecution execution, Log logger)
			throws MojoExecutionException, MojoFailureException {
		if (targetPlatforms.contains(",")) {
			for (String str : targetPlatforms.split(","))
				handle(str, execution, logger);
		}
		else handle(targetPlatforms, execution, logger);
	}

	private static void handle(String targetPlatform, MojoExecution execution, Log logger)
			throws MojoExecutionException, MojoFailureException {
		if (targetPlatform.equalsIgnoreCase("native")) {
			try {
				execution.execute(CompilerTools.getNativeTarget());
			}
			catch (UnsupportedOperationException e) {
				logger.warn(e.getMessage());
			}
		}
		else if (targetPlatform.equalsIgnoreCase("all")) {
			for (CompileTarget target : CompileTarget.values()) {
				try {
					execution.execute(target);
				}
				catch (UnsupportedOperationException e) {
					logger.warn(e.getMessage());
				}
			}
		}
		else {
			CompileTarget target = CompileTarget.valueOf(targetPlatform.toUpperCase());
			if (target != null) {
				try {
					execution.execute(target);
				}
				catch (UnsupportedOperationException e) {
					logger.warn(e.getMessage());
				}
			}
		}
	}
}
