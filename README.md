# gcc-maven-plugin
a really simple maven plugin to compile C/C++ code with GCC

Feel free to use, modify, PR, whatever. I made this for my own maven projects that had JNI libraries.

You don't _nessecarily_ have to use GCC (there are settings to change flags/compilers), but all the default
settings are made for GCC. If you want to compile for multiple platforms, you will need to add multiple
executions.

Example usage (excerpt from one of my own projects):

            <plugin>
                <groupId>ca.jarcode</groupId>
                <artifactId>gcc-maven-plugin</artifactId>
                <version>1.4</version>
                <executions>
                    <execution>
                        <id>compile-library</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <arguments>-O2 -Wall -D_REENTRANT -I${env.JAVA_HOME}/include -I${env.JAVA_HOME}/include/linux -Itarget/include -I/usr/include/luajit-2.0 -I/usr/include/luajit -fPIC</arguments>
                        </configuration>
                    </execution>
                    <execution>
                        <id>link-library</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>link</goal>
                        </goals>
                        <configuration>
                            <arguments>-Lsrc/main/resources -lluajit-5.1 -lffi</arguments>
                            <library>true</library>
                            <targetName>libcomputerimpl.so</targetName>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

There are two goals in this plugin `link` and `compile`, which do exactly as they suggest. The `compile` goal passes the `-c` flag to gcc, and both flags will pass `-o` following the output file for the goal.

Sources are searched (recursively) in `src/main/c` by default, and their respective objects are compiled and stored in `target/objects`. Targets (executables and libraries) produced during the linking goal are placed in `target/natives`.

note: arguments are split on spaces, so adding arguments that point to directories with spaces could be difficult (string encapsulation is not parsed)

liscense: GPLv3
