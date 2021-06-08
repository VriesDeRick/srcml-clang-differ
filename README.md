# srcML - Clang tree differ
This repository contains a Kotlin-based tool that assists in comparing trees produced by srcML and Clang. It highlights differences between the two trees, which can be used to find potential parsing bugs. 

## CLI reference
The differ supports a number of CLI arguments for compatibility and debugging modes. The list below explains the various arguments. This can also be obtained using the `--help` option. 

- `--srcml` Path to the srcML executable. Defaults to finding `srcml` in the PATH. 
- `--clang` Path to the Clang or Clang++ executable. Flags are added by the differ to enable C++-mode for Clang, so both the path to the `clang` and `clang++` executable should work. Defaults to finding `clang++` in the PATH. 
- `--clang-options` Options to pass to the Clang compiler and preprocessor. Can be used to include libraries, indicate C++ versions and set various other flags. The arguments are provided verbatim to Clang. Should be wrapped by quotes if the options-string contains spaces. 
- `--write-tools` Debugging option to enable writing the direct outputs from the tools in the current working directory. The files from srcML and Clang will be named `tool_srcml.xml` and `tool_clang.json`, respectively. If these files are already present in the current working directory, they will be overwritten. 

The final argument should always be the (relative or absolute) path to the C++ file that should be analyzed, regardless of the presence of any flags. A brief explanation of all flags can also be obtained using `--help`. 

## Output format
If the trees produced by both parsers represent the same grammatical structure, a single tree in the generic AST format is printed in green using ANSI color escape sequences. If a difference is recognized somewhere, the diverging trees from that point on will be printed in red. There are three other colors that can appear in the output: 
- If some node is colored magenta, it appeared in one of the trees but not in the other, and was suspected to be an implicit intermediate node and not an actual parsing error. If this is for some other node than a call to a constructor, it should still be investigated, since some parsing bugs do cause "skipped" nodes. 
- If the order in a multi-child node is skipped (such as a block statement), that node is marked blue. Note that this coloring can also occur if an error occurred in a child node of the subtree, even if the ordering itself was fine in that node. 
- If an error occurred in some node, all parent nodes of that node are colored yellow. This helps to easily identify the location of some error in the original code. 

Since the colors are the only supported way of displaying the result, there is no way to disable the built-in coloring.  