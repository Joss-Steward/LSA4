package FileSystemApp;

/**
* FileSystemApp/FileCopyHolder.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from FileSystem.idl
* Tuesday, December 13, 2016 9:27:04 PM EST
*/

public final class FileCopyHolder implements org.omg.CORBA.portable.Streamable
{
  public FileSystemApp.FileCopy value = null;

  public FileCopyHolder ()
  {
  }

  public FileCopyHolder (FileSystemApp.FileCopy initialValue)
  {
    value = initialValue;
  }

  public void _read (org.omg.CORBA.portable.InputStream i)
  {
    value = FileSystemApp.FileCopyHelper.read (i);
  }

  public void _write (org.omg.CORBA.portable.OutputStream o)
  {
    FileSystemApp.FileCopyHelper.write (o, value);
  }

  public org.omg.CORBA.TypeCode _type ()
  {
    return FileSystemApp.FileCopyHelper.type ();
  }

}
