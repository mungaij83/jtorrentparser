/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package za.co.pas.jtorrentparser.parser;

/**
 *
 * @author alabuschagne
 */
public class IllegalCharacterException extends Exception
{
    public IllegalCharacterException()
    {
        super();
    }

    public IllegalCharacterException(String str)
    {
        super(str);
    }
}
