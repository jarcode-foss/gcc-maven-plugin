package ca.jarcode.gcc.plugin;

public enum CompileTarget {
	// these are compiling and linking targets.
	// ELF applies to anything that uses the ELF executable/library format,
	// like Linux and FreeBSD.

	// OSX is not supported for cross-compiling, but mac users can certainly
	// compile natively.

	// Windows compile and linking targets assume the use of MinGW, in a
	// Unix-like environment, so Linux users will need MinGW installed,
	// and Windows users will have to use MinGW's shell (or something
	// similar).
	WIN32, WIN64, OSX, ELF32, ELF64
}
