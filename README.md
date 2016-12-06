# LSA PROJECT 4
## Distributed Filesystem

Corba something-something, distributed other-thing

## Deploying

All of the output files can be raked into the bin directory by running `gatherBins.sh`.
This is to make running the project on the servers easier.
Additionally, the compiled .class files have been removed from the `bin/.gitignore`, also to make deploying easier.

## Adding methods

OK. So. CORBA. CORBA CORBA CORBA.

Basically, we're going to need to add some methods to CORBA, which will then be turned into auto generated code, which is then used by both the client and the server.  It turns out that this isn't... *too* hard.

Basically:

	1) Define your interface in FileSystem/FileSystem.idl

	2) `cd FileSystem && idlj -fserver -fclient FileSystem.idl`

Done. Now, you need to edit `FileSystemServer/FileSystemServer.java` to implement the new methods.

*Note: You might have to reload some of the files in the FileSystem project to get eclipse to realize that they've changed*

