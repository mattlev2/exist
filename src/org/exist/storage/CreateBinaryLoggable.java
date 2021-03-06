/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 *
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.exist.storage.journal.LogException;
import org.exist.storage.txn.Txn;

/**
 * @author Adam Retter <adam@evolvedbinary.com>
 *
 * Serialized binary format is as follows:
 *
 * [walDataPathLen, walDataPath, createPathLen, createPath]
 *
 * walDataPathLen:  2 bytes, unsigned short
 * walDataPath:     var length bytes, UTF-8 encoded java.lang.String
 * createPathLen:   2 bytes, unsigned short
 * createPath:      var length bytes, UTF-8 encoded java.lang.String
 */
public class CreateBinaryLoggable extends AbstractBinaryLoggable {
    private byte[] walDataPath;  // the data to use for the file to be created
    private byte[] createPath;   // the file to be created

    /**
     * Creates a new instance of CreateBinaryLoggable.
     *
     * @param broker The database broker.
     * @param txn The database transaction.
     * @param walData A copy of the data that was stored for {@code create} file before it was actually created (i.e. the new value).
     * @param create The file that is to be created in the database.
     */
    public CreateBinaryLoggable(final DBBroker broker, final Txn txn, final Path walData, final Path create) {
        super(NativeBroker.LOG_CREATE_BINARY, txn.getId());
        this.walDataPath = getPathData(walData);
        checkPathLen(getClass().getSimpleName(), "walDataPath", walDataPath);
        this.createPath = getPathData(create);
        checkPathLen(getClass().getSimpleName(), "createPath", createPath);
    }

    /**
     * Creates a new instance of CreateBinaryLoggable.
     *
     * @param broker The database broker.
     * @param transactionId The database transaction id.
     */
    public CreateBinaryLoggable(final DBBroker broker, final long transactionId) {
        super(NativeBroker.LOG_CREATE_BINARY, transactionId);
    }

    @Override
    public int getLogSize() {
        return
                2 +
                walDataPath.length +
                2 +
                createPath.length;
    }

    @Override
    public void write(final ByteBuffer out) {
        out.putShort(asUnsignedShort(walDataPath.length));
        out.put(walDataPath);
        out.putShort(asUnsignedShort(createPath.length));
        out.put(createPath);
    }

    @Override
    public void read(final ByteBuffer in) {
        final int walDataPathLen = asSignedInt(in.getShort());
        this.walDataPath = new byte[walDataPathLen];
        in.get(walDataPath);

        final int createPathLen = asSignedInt(in.getShort());
        this.createPath = new byte[createPathLen];
        in.get(createPath);
    }

    @Override
    public void redo() throws LogException {
        //we need to re-copy the data from the walDataPath file to the createPath file

        final Path walData = getPath(walDataPath);
        final Path create = getPath(createPath);

        if (!Files.exists(walData)) {
            throw new LogException("Cannot redo creation of binary resource: "
                    + create.toAbsolutePath().toString() + ", missing write ahead data: "
                    + walData.toAbsolutePath().toString());
        }

        try {
            Files.copy(walData, create, StandardCopyOption.REPLACE_EXISTING);
        } catch(final IOException ioe) {
            throw new LogException("Cannot redo creation of binary resource: "
                    + create.toAbsolutePath().toString(), ioe);
        }
    }

    @Override
    public void undo() throws LogException {
        // we need to delete the createPath file

        final Path walData = getPath(walDataPath);
        final Path create = getPath(createPath);

        // ensure integrity of write-ahead-data file first!
        if (!Files.exists(walData)) {
            throw new LogException("Cannot redo creation of binary resource: "
                    + create.toAbsolutePath().toString() + ", missing write ahead data: "
                    + walData.toAbsolutePath().toString());
        }


        // cover the use-case where the previous operation was a delete
        if (!Files.exists(create)) {
            return;
        }

        try {
            Files.delete(create);
        } catch(final IOException ioe) {
            throw new LogException("Cannot undo creation of binary resource: "
                    + create.toAbsolutePath().toString(), ioe);
        }
    }

    public Path getCreateFile() {
        return getPath(createPath);
    }

    @Override
    public String dump() {
        return super.dump() + " - create binary [key=" + getPath(createPath).toAbsolutePath().toString() + ", currentValue=null, newValue=" + getPath(walDataPath).toAbsolutePath().toString() + "]";
    }
}
