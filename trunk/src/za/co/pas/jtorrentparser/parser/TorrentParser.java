/*
 * Copyright 2010 Paranoid Android Software cc
 * http://www.ParanoidAndroid.co.za
 *
 * This computer program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 *
 * This computer program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this computer program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package za.co.pas.jtorrentparser.parser;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import za.co.pas.jtorrentparser.util.IntWrapper;

/**
 *
 * @author André Labuschagné
 */
public class TorrentParser
{
    private static final short STATE_NONE = 0;
    private static final short STATE_MAP = 1;
    private static final short STATE_LIST = 2;
    private static final short STATE_INT = 3;
    private static final short STATE_STR = 4;
    private static final short READ_STATE_SIZE = 0;
    private static final short READ_STATE_KEY = 1;
    private static final short READ_STATE_VALUE = 2;

    /**
     * Main entry to parse a Torrent File.
     * @param bytes the content of the torrent file as a byte array
     * @return a map containing the torrent file's data
     * @throws IllegalCharacterException
     * @throws FileFormatException
     */
    public static Map<String, Object> parse(short[] bytes) throws IllegalCharacterException, FileFormatException
    {
        short state = STATE_NONE;
        Map<String, Object> map = new HashMap<String, Object>();
        IntWrapper index = new IntWrapper(0);
        while(index.i < bytes.length)
        {
            short b = bytes[index.i];
            switch(state)
            {
                case STATE_NONE:
                {
                    switch((char) b)
                    {
                        case 'd':
                        {
                            state = STATE_MAP;
                            break;
                        }
                        case 'l':
                        {
                            state = STATE_LIST;
                            break;
                        }
                        case 'i':
                        {
                            state = STATE_INT;
                            break;
                        }
                        case 'e':
                        {
                            return map;
                        }
                        default:
                        {
                            state = STATE_STR;
                            break;
                        }
                    }
                    index.i++;
                    break;
                }
                case STATE_MAP:
                {
                    map = parseMap(bytes, index);
                    state = STATE_NONE;
                    break;
                }
                default:
                {
                    throw new IllegalCharacterException(prepareIllegalCharError((char) b, index.i, bytes).toString());
                }
            }
        }
        return map;
    }

    /**
     * 
     * @param bytes
     * @param startIndex
     * @return
     * @throws IllegalCharacterException
     * @throws NumberFormatException
     * @throws FileFormatException
     */
    public static Map<String, Object> parseMap(short[] bytes, IntWrapper startIndex) throws IllegalCharacterException, NumberFormatException, FileFormatException
    {
        boolean firstTime = true;
        int si = startIndex.i;
        Map<String, Object> map = new HashMap<String, Object>();
        short readState = READ_STATE_KEY;
        String key = "";
        while(startIndex.i < bytes.length)
        {
            short b = bytes[startIndex.i];
            switch(readState)
            {
                case READ_STATE_KEY:
                {
                    char c = (char) b;
                    if((c >= '0') && (c <= '9'))
                    {
                        key = parseString(bytes, startIndex);
                        readState = READ_STATE_VALUE;
                    }
                    else if(c == 'e')
                    {
                        startIndex.i++;
                        return map;
                    }
                    else
                    {
                        throw new IllegalCharacterException(prepareIllegalCharError(c, startIndex.i, bytes).toString());
                    }
                    break;
                }
                case READ_STATE_VALUE:
                {
                    char c = (char) b;
                    if((c >= '0') && (c <= '9'))
                    {
                        short[] bValue = parseBytes(bytes, startIndex);
                        map.put(key, bValue);
                    }
                    else if(c == 'l')
                    {
                        List lValue = parseList(bytes, startIndex);
                        map.put(key, lValue);
                    }
                    else if(c == 'i')
                    {
                        Integer iValue = parseInt(bytes, startIndex);
                        map.put(key, iValue);
                    }
                    else if(c == 'd')
                    {
                        startIndex.i++;
                        Map mValue = parseMap(bytes, startIndex);
                        map.put(key, mValue);
                    }
                    else if(c == 'e')
                    {
                        return map;
                    }
                    else
                    {
                        throw new IllegalCharacterException(prepareIllegalCharError(c, startIndex.i, bytes).toString());
                    }
                    readState = READ_STATE_KEY;
                    break;
                }
            }
        }
        throw new FileFormatException("Reach end of file. Started at " + Integer.toString(si));
    }

    /**
     *
     * @param bytes
     * @param startIndex
     * @return
     * @throws NumberFormatException
     * @throws IllegalCharacterException
     * @throws FileFormatException
     */
    public static String parseString(short[] bytes, IntWrapper startIndex) throws NumberFormatException, IllegalCharacterException, FileFormatException
    {
        int si = startIndex.i;
        short readState = READ_STATE_SIZE;
        int sizeToRead = 0;
        int count = 0;
        StringBuilder str = new StringBuilder();
        while(startIndex.i < bytes.length)
        {
            short b = bytes[startIndex.i];
            switch(readState)
            {
                case READ_STATE_SIZE:
                {
                    char c = (char) b;
                    if((c >= '0') && (c <= '9'))
                    {
                        str.append(c);
                    }
                    else if(c == ':')
                    {
                        sizeToRead = Integer.parseInt(str.toString());
                        readState = READ_STATE_VALUE;
                        str = new StringBuilder();
                    }
                    else
                    {
                        throw new IllegalCharacterException(prepareIllegalCharError(c, startIndex.i, bytes).toString());
                    }
                    startIndex.i++;
                    break;
                }
                case READ_STATE_VALUE:
                {
                    if(count >= sizeToRead)
                    {
                        //startIndex.i++;
                        return str.toString();
                    }
                    else
                    {
                        str.append((char) b);
                        startIndex.i++;
                    }
                    count++;
                    break;
                }
            }
        }
        throw new FileFormatException("Reach end of file. Started at " + Integer.toString(si));
    }

    /**
     *
     * @param bytes
     * @param startIndex
     * @return
     * @throws NumberFormatException
     * @throws IllegalCharacterException
     * @throws FileFormatException
     */
    public static short[] parseBytes(short[] bytes, IntWrapper startIndex) throws NumberFormatException, IllegalCharacterException, FileFormatException
    {
        int si = startIndex.i;
        short readState = READ_STATE_SIZE;
        int sizeToRead = 0;
        int count = 0;
        StringBuilder str = new StringBuilder();
        short[] ret = null;
        while(startIndex.i < bytes.length)
        {
            short b = bytes[startIndex.i];
            switch(readState)
            {
                case READ_STATE_SIZE:
                {
                    char c = (char) b;
                    if((c >= '0') && (c <= '9'))
                    {
                        str.append(c);
                    }
                    else if(c == ':')
                    {
                        sizeToRead = Integer.parseInt(str.toString());
                        ret = new short[sizeToRead];
                        readState = READ_STATE_VALUE;
                    }
                    else
                    {
                        throw new IllegalCharacterException(prepareIllegalCharError(c, startIndex.i, bytes).toString());
                    }
                    startIndex.i++;
                    break;
                }
                case READ_STATE_VALUE:
                {
                    if(count >= sizeToRead)
                    {
                        return ret;
                    }
                    else
                    {
                        ret[count] = b;
                        startIndex.i++;
                    }
                    count++;
                    break;
                }
            }
        }
        throw new FileFormatException("Reach end of file. Started at " + Integer.toString(si));
    }

    /**
     *
     * @param bytes
     * @param startIndex
     * @return
     * @throws IllegalCharacterException
     * @throws FileFormatException
     */
    public static List parseList(short[] bytes, IntWrapper startIndex) throws IllegalCharacterException, FileFormatException
    {
        boolean firstList = true;
        int si = startIndex.i;
        List ret = new LinkedList();
        while(startIndex.i < bytes.length)
        {
            short b = bytes[startIndex.i];
            char c = (char) b;
            if((c >= '0') && (c <= '9'))
            {
                short[] bs = parseBytes(bytes, startIndex);
                ret.add(bs);
            }
            else if(c == 'l')
            {
                if(firstList)
                {
                    firstList = false;
                    //do nothing
                    startIndex.i++;
                }
                else
                {
                    List innerList = parseList(bytes, startIndex);
                    ret.add(innerList);
                }
            }
            else if(c == 'e')
            {
                startIndex.i++;
                return ret;
            }
            else if(c == 'd')
            {
                startIndex.i++;
                Map<String, Object> m = parseMap(bytes, startIndex);
                ret.add(m);
            }
            else if(c == 'i')
            {
                startIndex.i++;
                Integer I = parseInt(bytes, startIndex);
                ret.add(I);
            }
            else
            {
                throw new IllegalCharacterException(prepareIllegalCharError(c, startIndex.i, bytes).toString());
            }
        }
        throw new FileFormatException("Reach end of file. Started at " + Integer.toString(si));
    }

    /**
     *
     * @param c
     * @param index
     * @param bytes
     * @return
     */
    private static StringBuilder prepareIllegalCharError(char c, int index, short[] bytes)
    {
        StringBuilder sb = new StringBuilder("Illegal char '");
        sb.append(c);
        sb.append("' at ");
        sb.append(index);
        //now some more text around the error problem (+- 5 chars)
        int start = index - 5;
        int end = index + 5;
        if(start < 0)
        {
            start = 0;
        }
        if(end >= bytes.length)
        {
            end = bytes.length - 1;
        }
        sb.append(" \"");
        for(int i = start; i < end; i++)
        {
            sb.append((char) bytes[i]);
        }
        sb.append("\"");
        return sb;
    }

    /**
     * 
     * @param bytes
     * @param startIndex
     * @return
     * @throws IllegalCharacterException
     * @throws NumberFormatException
     * @throws FileFormatException
     */
    public static Integer parseInt(short[] bytes, IntWrapper startIndex) throws IllegalCharacterException, NumberFormatException, FileFormatException
    {
        int si = startIndex.i;
        Integer ret = null;
        StringBuilder str = new StringBuilder();
        while(startIndex.i < bytes.length)
        {
            short b = bytes[startIndex.i];
            char c = (char) b;
            if((c >= '0') && (c <= '9'))
            {
                str.append(c);
            }
            else if(c == 'i')
            {
                //do nothing
            }
            else if(c == 'e')
            {
                ret = new Integer(str.toString());
                startIndex.i++;
                return ret;
            }
            else
            {
                throw new IllegalCharacterException(prepareIllegalCharError(c, startIndex.i, bytes).toString());
            }
            startIndex.i++;
        }
        throw new FileFormatException("Reach end of file. Started at " + Integer.toString(si));
    }

    public static Map sortByValue(Map map)
    {
        List list = new LinkedList(map.entrySet());
        Collections.sort(list, new Comparator()
        {
            public int compare(Object o1, Object o2)
            {
                return ((Comparable) ((Map.Entry) (o1)).getValue()).compareTo(((Map.Entry) (o2)).getValue());
            }
        });

        Map result = new LinkedHashMap();
        for(Iterator it = list.iterator(); it.hasNext();)
        {
            Map.Entry entry = (Map.Entry) it.next();
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Takes a torrent map and returns a byte array. Use this to write out a 
     * torrent file.  This is not optimized :(
     * @param map torrent map file
     * @return byte array of torrent data
     */
    /*public static byte[] toBytes(Map<String, Object> map)
    {
    //first order keys alphabetically
    List sortedKeys = new ArrayList(map.keySet());
    Collections.sort(sortedKeys);
    List<Byte> out = new LinkedList<Byte>();
    out.add(new Byte((byte) 'd'));
    ListIterator<String> it = sortedKeys.listIterator();
    while(it.hasNext())
    {
    //Add the key
    String key = it.next();
    addToByteList(toBytes(key), out);
    //add the value
    if(map.get(key) instanceof byte[])
    {
    addToByteList(toBytes((byte[]) map.get(key)), out);
    }
    else if(map.get(key) instanceof String)
    {
    addToByteList(toBytes((String) map.get(key)), out);
    }
    else if(map.get(key) instanceof Integer)
    {
    addToByteList(toBytes((Integer) map.get(key)), out);
    }
    else if(map.get(key) instanceof Map)
    {
    addToByteList(toBytes((Map) map.get(key)), out);
    }
    else if(map.get(key) instanceof List)
    {
    addToByteList(toBytes((List) map.get(key)), out);
    }
    }

    out.add(new Byte((byte) 'e'));
    return convert(out);
    }

    public static byte[] toBytes(List list)
    {
    List<Byte> out = new LinkedList<Byte>();
    out.add(new Byte((byte) 'l'));
    ListIterator it = list.listIterator();
    while(it.hasNext())
    {
    Object o = it.next();
    if(o instanceof byte[])
    {
    addToByteList(toBytes((byte[]) o), out);
    }
    else if(o instanceof String)
    {
    addToByteList(toBytes((String) o), out);
    }
    else if(o instanceof Integer)
    {
    addToByteList(toBytes((Integer) o), out);
    }
    else if(o instanceof Map)
    {
    addToByteList(toBytes((Map) o), out);
    }
    else if(o instanceof List)
    {
    addToByteList(toBytes((List) o), out);
    }
    }

    out.add(new Byte((byte) 'e'));
    return convert(out);
    }

    public static byte[] toBytes(String str)
    {
    return toBytes(str.getBytes());
    }

    public static byte[] toBytes(byte[] bytes)
    {
    List<Byte> out = new LinkedList<Byte>();
    //Add the lenght of the byte array
    String len = Integer.toString(bytes.length);
    for(int i = 0; i < len.length(); i++)
    {
    out.add(new Byte((byte) len.charAt(i)));
    }
    out.add(new Byte((byte) ':'));
    //add the actual byte array
    for(int i = 0; i < bytes.length; i++)
    {
    out.add(new Byte(bytes[i]));
    }
    return convert(out);
    }

    public static byte[] toBytes(Integer integer)
    {
    List<Byte> out = new LinkedList<Byte>();
    //Add the lenght of the byte array
    out.add(new Byte((byte) 'i'));
    String len = Integer.toString(integer);
    for(int i = 0; i < len.length(); i++)
    {
    out.add(new Byte((byte) len.charAt(i)));
    }
    out.add(new Byte((byte) 'e'));
    return convert(out);
    }

    private static byte[] convert(List<Byte> list)
    {
    byte[] retBytes = new byte[list.size()];
    for(int i = 0; i < list.size(); i++)
    {
    retBytes[i] = ((Byte) list.get(i)).byteValue();
    }
    return retBytes;
    }

    private static void addToByteList(byte[] bytes, List<Byte> list)
    {
    for(int i = 0; i < bytes.length; i++)
    {
    list.add(new Byte(bytes[i]));
    }
    }*/
    /**
     * Takes a torrent map and returns a byte array. Use this to write out a
     * torrent file.  This is not optimized :(
     * @param map torrent map file
     * @return byte array of torrent data
     */
    public static void toBytes(Map<String, Object> map, OutputStream baos) throws IOException
    {
        //first order keys alphabetically
        List sortedKeys = new ArrayList(map.keySet());
        Collections.sort(sortedKeys);
        baos.write((short) 'd');
        ListIterator<String> it = sortedKeys.listIterator();
        while(it.hasNext())
        {
            //Add the key
            String key = it.next();
            toBytes(key, baos);
            //add the value
            if(map.get(key) instanceof short[])
            {
                toBytes((short[]) map.get(key), baos);
            }
            else if(map.get(key) instanceof String)
            {
                toBytes((String) map.get(key), baos);
            }
            else if(map.get(key) instanceof Integer)
            {
                toBytes((Integer) map.get(key), baos);
            }
            else if(map.get(key) instanceof Map)
            {
                toBytes((Map) map.get(key), baos);
            }
            else if(map.get(key) instanceof List)
            {
                toBytes((List) map.get(key), baos);
            }
        }

        baos.write((short) 'e');
    }

    public static void toBytes(List list, OutputStream baos) throws IOException
    {
        baos.write((short) 'l');
        ListIterator it = list.listIterator();
        while(it.hasNext())
        {
            Object o = it.next();
            if(o instanceof short[])
            {
                toBytes((short[]) o, baos);
            }
            else if(o instanceof String)
            {
                toBytes((String) o, baos);
            }
            else if(o instanceof Integer)
            {
                toBytes((Integer) o, baos);
            }
            else if(o instanceof Map)
            {
                toBytes((Map) o, baos);
            }
            else if(o instanceof List)
            {
                toBytes((List) o, baos);
            }
        }
        baos.write((short) 'e');
    }

    public static void toBytes(String str, OutputStream baos) throws IOException
    {
        byte[] b = str.getBytes();
        short[] s = new short[b.length];
        for(int i = 0; i < s.length; i++)
        {
            s[i] = (short) b[i];
        }
        toBytes(s, baos);
    }

    public static void toBytes(short[] bytes, OutputStream baos) throws IOException
    {
        //Add the lenght of the byte array
        String len = Integer.toString(bytes.length);
        for(int i = 0; i < len.length(); i++)
        {
            baos.write((short) len.charAt(i));
        }
        baos.write((short) ':');
        //add the actual byte array
        for(int i = 0; i < bytes.length; i++)
        {
            baos.write(bytes[i]);
        }
    }

    public static void toBytes(Integer integer, OutputStream baos) throws IOException
    {
        //Add the lenght of the byte array
        baos.write((short) 'i');
        String len = Integer.toString(integer);
        for(int i = 0; i < len.length(); i++)
        {
            baos.write((short) len.charAt(i));
        }
        baos.write((short) 'e');
    }
}
