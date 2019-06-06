package neoe.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class BAOS extends OutputStream {

	/**
	 * The buffer where data is stored.
	 */
	public byte buf[];

	/**
	 * The number of valid bytes in the buffer.
	 */
	protected int count;

	public BAOS(int initSize, int maxSize) {
		if (initSize <= 0) {
			initSize = 1024 * 4;
		}
		if (maxSize > 0)
			this.MAX_SIZE = maxSize;
		buf = new byte[initSize];
	}

	private void ensureCapacity(int minCapacity) {
		// overflow-conscious code
		if (minCapacity - buf.length > 0)
			grow(minCapacity);
	}

	private int MAX_SIZE = Integer.MAX_VALUE - 8;

	private int maxCount;

	/**
	 * Increases the capacity to ensure that it can hold at least the number of
	 * elements specified by the minimum capacity argument.
	 *
	 * @param minCapacity
	 *            the desired minimum capacity
	 */
	private void grow(int minCapacity) {
		// overflow-conscious code
		if (minCapacity > MAX_SIZE) {
			throw new RuntimeException("overflow max_size:" + MAX_SIZE);
		}
		int oldCapacity = buf.length;
		int max_inc = 16 * 1024 * 1024;
		int newCapacity = oldCapacity > max_inc ? oldCapacity + max_inc : oldCapacity + oldCapacity;
		if (newCapacity > MAX_SIZE) {
			newCapacity = MAX_SIZE;
		}
		buf = Arrays.copyOf(buf, newCapacity);
	}

	/**
	 * Writes the specified byte to this byte array output stream.
	 *
	 * @param b
	 *            the byte to be written.
	 */
	public void write(int b) {
		ensureCapacity(count + 1);
		buf[count] = (byte) b;
		count += 1;
	}

	/**
	 * Writes <code>len</code> bytes from the specified byte array starting at
	 * offset <code>off</code> to this byte array output stream.
	 *
	 * @param b
	 *            the data.
	 * @param off
	 *            the start offset in the data.
	 * @param len
	 *            the number of bytes to write.
	 */
	public void write(byte b[], int off, int len) {
		if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) - b.length > 0)) {
			throw new IndexOutOfBoundsException();
		}
		ensureCapacity(count + len);
		System.arraycopy(b, off, buf, count, len);
		count += len;
	}

	public void writeTo(OutputStream out) throws IOException {
		out.write(buf, 0, Math.max(maxCount, count));
	}

	public void reset() {
		count = 0;
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(buf, count);
	}

	public int size() {
		return Math.max(maxCount, count);
	}

	public void close() throws IOException {
	}

	public String toString() {
		return new String(buf, 0, Math.max(maxCount, count));
	}

	public String toString(String charsetName) throws UnsupportedEncodingException {
		return new String(buf, 0, Math.max(maxCount, count), charsetName);
	}

	public void writeNBytes(int len) {
		ensureCapacity(count + len);
		count += len;
	}

	public int pos() {
		return count;
	}

	/** caution:not tested */
	public void setSize(int newsize) {
		ensureCapacity(newsize);
		maxCount = newsize;
	}

	public void seek(int pos) {
		if (pos > count) {
			writeNBytes(pos - count);
		} else {
			maxCount = Math.max(maxCount, count);
			count = pos;
		}
	}

}
