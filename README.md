# LSA PROJECT 4

## Building

All of the output files can be raked into the bin directory by running `gatherBins.sh`.
This is to make running the project on the servers easier. Additionally, the compiled .class files have been removed from the `bin/.gitignore`, also to make deploying easier.

## Running

Ok, whoever wants to run this, these instructions are for you:

For each host (dfsIreland, dfsVirginia, dfsJapan), you need to ssh in, do a `git pull` to get the latest stuff, and on each host run

`orbd -ORBInitialPort 1052 -port 1053 -ORBInitialHost <ip> -ORBServerHost <ip>`

and in a second ssh session

`cd LSA4/bin`
`java FileSystemApp.FileSystemServer -ORBInitialPort 1052 -port 1053 -ORBInitialHost <ip> -ORBServerHost <ip>`

and then in a third ssh session

`cd LSA4/bin`
`java java FileSystemApp.FileSystemClient -ORBInitialPort 1052 -ORBInitialHost <ip>`

where <ip> is the ip of that host (obviously). You'll need to get the ip's somehow, I usually just ssh into clipper and do `ping <host>` once for each of them, since it's easiest that way. You can *try* substituting the hostname for the ip, but I haven't had much success with that.

Something that I haven't done yet, but is *really* important, is on each host we need to remove the ip address of the current machine... oh wait, actually that might not be a problem. I dunno, try it sometime and see but I would 9/10 recommend it.

The last command opens up the client, which I'm a little proud of. I went for a retro kinda feel for the ui, and I think it came out well.

## TODO

The code meets all the project requirements, as far as I can tell, besides that itsy-bitsy bug. However, there's still some work we can do if we have time:

[ ] The code could use comments, it doesn't have many

[ ] We can shoot for the bonux objective, if we have time between this and web prog.











## Adding methods

OK. So. CORBA. CORBA CORBA CORBA.

Basically, we're going to need to add some methods to CORBA, which will then be turned into auto generated code, which is then used by both the client and the server.  It turns out that this isn't... *too* hard.

Basically:

	1) Define your interface in FileSystem/FileSystem.idl

	2) `cd FileSystem && idlj -fserver -fclient FileSystem.idl`

Done. Now, you need to edit `FileSystemServer/FileSystemServer.java` to implement the new methods.

*Note: You might have to reload some of the files in the FileSystem project to get eclipse to realize that they've changed*

