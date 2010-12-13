/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package za.co.pas.jtorrentparser.parser;

/**
 *
 * @author alabuschagne
 */
public class FileFormatException extends Exception
{
    public FileFormatException()
    {
        super();
    }

    public FileFormatException(String str)
    {
        super(str);
    }
}
