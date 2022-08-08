package DecompressOcf;

// attempt to translate my c++ extractor to java
// so that it is easier to use in nifi
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import java.nio.ByteBuffer;
import java.io.*;
import java.nio.file.*;


public class DecompressBlob {
    private int[] lzwLookupTableFirst, lzwLookupTableSecond;


    private int MAX_CODES= 8192;
    private int[] tempDecompressBuffer;
    private int tempBufferIndex;
    private int codeCount;
    private byte[] finalByteBuffer;
    private ArrayList<Byte> FBB;

    public DecompressBlob() {
	tempBufferIndex = 0;
	codeCount = 257;
	tempDecompressBuffer = new int[MAX_CODES];
	lzwLookupTableFirst = new int[MAX_CODES];
	lzwLookupTableSecond = new int[MAX_CODES];
	FBB = new ArrayList<Byte>();
    }

    
    private class BitPlaceHolder {
	int index, LeftOverBits, LeftOverCode;
	public BitPlaceHolder() {
	    index = 0;
	    LeftOverBits = 0;
	    LeftOverCode = 0;
	}
    }

    private int ReadNextCode(byte[] rawbytes,
			     int codesize,
			     BitPlaceHolder bph) {
	int code;
	// need to mask the first 3 bytes so that
	// the raw bytes don't get interpreted as
	// signed.
	int b0 = (0x000000FF) & rawbytes[bph.index];
	++bph.index;

	if (bph.LeftOverBits == 0)
	    {
		// byte aligned - always need 2 bytes here,
		int remainder = codesize - 8;
		int b1 = (0x000000FF) & rawbytes[bph.index];
		++bph.index;
		code = (b0 << remainder) | (b1 >> (8 - remainder));
		bph.LeftOverBits = 8 - remainder;
		bph.LeftOverCode = b1 & ((1 << bph.LeftOverBits) - 1);
	    }
	else
	    {
		int remainder = (int)bph.LeftOverBits + 8 - codesize;
		
		code = bph.LeftOverCode << (codesize - bph.LeftOverBits);
		if (remainder < 0)
		    {
			// use the entire byte
			code = code | (b0 << -remainder);
			// need another byte
			b0 = (0x000000FF) & rawbytes[bph.index];
			++bph.index;
			remainder = bph.LeftOverBits + 16 - codesize;
			code = code | (b0 >> remainder);
		    }
		else
		    {
			code = code | (b0 >> remainder);
		    }
		
		bph.LeftOverBits = remainder;
		bph.LeftOverCode = b0 & ((1 << bph.LeftOverBits) - 1);
	    }
	return(code);
    }


    private void SaveItemToLookupTable( int compressedCode) {
	tempBufferIndex = -1;
	while (compressedCode >= 258)
	    {
		tempDecompressBuffer[++tempBufferIndex] = lzwLookupTableSecond[compressedCode];
		compressedCode = lzwLookupTableFirst[compressedCode];
	    }
	tempDecompressBuffer[++tempBufferIndex] = compressedCode;
	for (int i = tempBufferIndex; i >= 0; i--)
	    {
		FBB.add((byte)tempDecompressBuffer[i]);
	    }
    }

    public ArrayList<Byte> Decompress2AL(byte[] rawbytes) {
	final int bytesize = 8;
	int stringsize = rawbytes.length;
	FBB.clear();
	int codesize = 9;
  
	BitPlaceHolder bph = new BitPlaceHolder();
	int prevCode = 0;
	while (bph.index < stringsize)
	    {
		int lookupIndex = ReadNextCode(rawbytes, codesize, bph);
		if (lookupIndex == 256)  // time to move to a new lookup table
		    {
			codesize = 9;
			tempDecompressBuffer = new int[MAX_CODES];
			tempBufferIndex = 0;
			lzwLookupTableFirst = new int[MAX_CODES];
			lzwLookupTableSecond = new int[MAX_CODES];
			codeCount           = 257;
			continue;
		    }
		else if (lookupIndex == 257) // EOF marker, better than using the string size
		    {
			// copy FBB to finalByteBuffer
			// should be a nicer way ...
			return FBB;
			//finalByteBuffer = new byte[FBB.size()];
			//Iterator<Byte> iter = FBB.iterator();
			//int pos = 0;
			//while (iter.hasNext()) {
			//    finalByteBuffer[pos++] = iter.next();
			//}
			//return finalByteBuffer;
		    }

		if (prevCode == 0)
		    {
			tempDecompressBuffer[0] = lookupIndex;
		    }
		if (lookupIndex < codeCount)
		    {
			SaveItemToLookupTable(lookupIndex);
			if (codeCount < MAX_CODES)
			    {
				lzwLookupTableFirst[codeCount] = prevCode;
				lzwLookupTableSecond[codeCount] = tempDecompressBuffer[tempBufferIndex];
				++codeCount;
			    }
		    }
		else
		    {
			lzwLookupTableFirst[codeCount] = prevCode;
			lzwLookupTableSecond[codeCount] = tempDecompressBuffer[tempBufferIndex];
			++codeCount;
			SaveItemToLookupTable(lookupIndex);
		    }
		
		switch (codeCount)  // use the lookup table size and not the current byte count
		    {
		    case 511:
		    case 1023:
		    case 2047:
		    case 4095:
			codesize++;
			break;
		    }
		prevCode = lookupIndex;
	    }
	return FBB;
    }

    public ArrayList<Byte> Decompress(ArrayList<Byte> rawbytes) {
	byte [] input = new byte[rawbytes.size()];
	Iterator<Byte> iter = rawbytes.iterator();
	int pos = 0;
	while (iter.hasNext()) {
	    input[pos++] = iter.next();
	}
	ArrayList<Byte> result = Decompress2AL(input);
	return result;
    }

    public byte[] DecompressBB(ByteBuffer BB) {
	if (BB.hasArray()) {
	    return(Decompress(BB.array()));
	} else {
	    byte [] input = new byte[BB.remaining()];
	    BB.get(input);
	    return(Decompress(input));
	}
    }
    public int Test() {
	int x = 1;
	return(x);
    }
    public byte[] Decompress(byte[] rawbytes) {
	ArrayList<Byte> X = Decompress2AL(rawbytes);
	byte[] finalByteBuffer = new byte[X.size()];
	Iterator<Byte> iter = X.iterator();
	int pos = 0;
	while (iter.hasNext()) {
	    finalByteBuffer[pos++] = iter.next();
	}
	return finalByteBuffer;	
    }
    
    //    public static void main(String[] args) throws IOException, InterruptedException {
    //	if (args.length > 0) {
	    //System.out.println(args[0]);
	    // File infile =  new File(args[0]);
	    // Path path = Paths.get(infile.getAbsolutePath());
	    // byte[] fileContent = Files.readAllBytes(path);
	    // DecompressBlob D = new DecompressBlob();

	    // speed test
            // long startTime = System.nanoTime();
	    // for (int i = 0;i < 10000; i++) {
	    // 	DecompressBlob Dtest = new DecompressBlob();
	    // 	byte[] dec = Dtest.Decompress(fileContent);
	    // }
	    // long endTime = System.nanoTime();
	    // System.out.println((endTime - startTime)/1000000);
		
    // 	    fileContent = D.Decompress(fileContent);
    // 	    File outfile = new File(args[1]);
    // 	    Path outpath = Paths.get(outfile.getAbsolutePath());
    // 	    Files.write(outpath, fileContent);
    // 	}
    // 	else
    // 	    {
    // 		System.err.println("Need a file argument");
    // 	    }
	
	
    // }
}
