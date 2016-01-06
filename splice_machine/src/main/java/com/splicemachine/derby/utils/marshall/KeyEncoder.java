package com.splicemachine.derby.utils.marshall;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.derby.utils.marshall.dvd.DescriptorSerializer;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Scott Fines
 *         Date: 11/15/13
 */
public class KeyEncoder implements Closeable{
    private final HashPrefix prefix;
    private final DataHash hash;
    private final KeyPostfix postfix;

    public KeyEncoder(HashPrefix prefix,DataHash hash,KeyPostfix postfix){
        this.prefix=prefix;
        this.hash=hash;
        this.postfix=postfix;
    }

    @Override
    public void close() throws IOException{
        prefix.close();
        hash.close();
        postfix.close();
    }

    public byte[] getKey(ExecRow row) throws StandardException, IOException{
        hash.setRow(row);
        byte[] hashBytes=hash.encode();

        int prefixLength=prefix.getPrefixLength();
        int totalLength=prefixLength+hashBytes.length;
        int postfixOffset=prefixLength+hashBytes.length;
        int postfixLength=postfix.getPostfixLength(hashBytes);
        if(postfixLength>0){
            totalLength++;
            postfixOffset++;
        }
        totalLength+=postfixLength;
        byte[] finalRowKey=new byte[totalLength];
        prefix.encode(finalRowKey,0,hashBytes);
        if(hashBytes.length>0){
            System.arraycopy(hashBytes,0,finalRowKey,prefixLength,hashBytes.length);
        }
        if(postfixLength>0){
            finalRowKey[prefixLength+hashBytes.length]=0x00;
        }
        postfix.encodeInto(finalRowKey,postfixOffset,hashBytes);

        return finalRowKey;
    }

    public KeyDecoder getDecoder(){
        return new KeyDecoder(hash.getDecoder(),prefix.getPrefixLength());
    }

    public static KeyEncoder bare(int[] groupColumns,boolean[] groupSortOrder,DescriptorSerializer[] serializers){
        return new KeyEncoder(NoOpPrefix.INSTANCE,BareKeyHash.encoder(groupColumns,groupSortOrder,serializers),NoOpPostfix.INSTANCE);
    }
}
