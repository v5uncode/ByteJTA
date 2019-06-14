/**
 * Copyright 2014-2016 yangming.liu<bytefox@126.com>.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 */
package org.bytesoft.bytejta.supports.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.commons.lang3.StringUtils;
import org.bytesoft.common.utils.ByteUtils;
import org.bytesoft.transaction.xa.TransactionXid;
import org.bytesoft.transaction.xa.XidFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecoveredResource extends LocalXAResource implements XAResource {
	static final Logger logger = LoggerFactory.getLogger(RecoveredResource.class);

	private DataSource dataSource;

	public void recoverable(Xid xid) throws XAException {
		byte[] globalTransactionId = xid.getGlobalTransactionId();
		byte[] branchQualifier = xid.getBranchQualifier();

		String identifier = this.getIdentifier(globalTransactionId, branchQualifier);

		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			conn = this.dataSource.getConnection();
			stmt = conn.prepareStatement("select gxid, bxid from bytejta where xid = ?");
			stmt.setString(1, identifier);
			rs = stmt.executeQuery();
			if (rs.next() == false) {
				throw new XAException(XAException.XAER_NOTA);
			}
		} catch (SQLException ex) {
			try {
				this.isTableExists(conn);
			} catch (SQLException sqlEx) {
				logger.warn("Error occurred while recovering local-xa-resource.", ex);
				throw new XAException(XAException.XAER_RMFAIL);
			} catch (RuntimeException rex) {
				logger.warn("Error occurred while recovering local-xa-resource.", ex);
				throw new XAException(XAException.XAER_RMFAIL);
			}

			throw new XAException(XAException.XAER_RMERR);
		} catch (RuntimeException ex) {
			logger.warn("Error occurred while recovering local-xa-resource.", ex);
			throw new XAException(XAException.XAER_RMERR);
		} finally {
			this.closeQuietly(rs);
			this.closeQuietly(stmt);
			this.closeQuietly(conn);
		}
	}

	public Xid[] recover(int flags) throws XAException {
		List<Xid> xidList = new ArrayList<Xid>();

		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			conn = this.dataSource.getConnection();
			stmt = conn.prepareStatement("select gxid, bxid from bytejta");
			rs = stmt.executeQuery();
			while (rs.next()) {
				String gxid = rs.getString(1);
				String bxid = rs.getString(2);
				byte[] globalTransactionId = ByteUtils.stringToByteArray(gxid);
				byte[] branchQualifier = ByteUtils.stringToByteArray(bxid);
				TransactionXid xid = null;
				if (StringUtils.equals(gxid, bxid)) {
					xid = new TransactionXid(XidFactory.JTA_FORMAT_ID, globalTransactionId);
				} else {
					xid = new TransactionXid(XidFactory.JTA_FORMAT_ID, globalTransactionId, branchQualifier);
				}
				xidList.add(xid);
			}
		} catch (Exception ex) {
			boolean tableExists = false;
			try {
				tableExists = this.isTableExists(conn);
			} catch (Exception sqlEx) {
				logger.warn("Error occurred while recovering local-xa-resource.", ex);
				throw new XAException(XAException.XAER_RMFAIL);
			}

			if (tableExists) {
				throw new XAException(XAException.XAER_RMERR);
			}
		} finally {
			this.closeQuietly(rs);
			this.closeQuietly(stmt);
			this.closeQuietly(conn);
		}

		Xid[] xidArray = new Xid[xidList.size()];
		xidList.toArray(xidArray);

		return xidArray;
	}

	public void forgetQuietly(Xid xid) {
		try {
			this.forget(xid);
		} catch (XAException ex) {
			logger.warn("Error occurred while forgeting local-xa-resource.", xid);
		}
	}

	public synchronized void forget(Xid[] xids) throws XAException {
		if (xids == null || xids.length == 0) {
			return;
		}

		String[] xidArray = new String[xids.length];

		for (int i = 0; i < xids.length; i++) {
			Xid xid = xids[i];

			byte[] globalTransactionId = xid.getGlobalTransactionId();
			byte[] branchQualifier = xid.getBranchQualifier();
			xidArray[i] = this.getIdentifier(globalTransactionId, branchQualifier);
		}

		Connection conn = null;
		PreparedStatement stmt = null;
		Boolean autoCommit = null;
		try {
			conn = this.dataSource.getConnection();
			autoCommit = conn.getAutoCommit();
			conn.setAutoCommit(false);
			stmt = conn.prepareStatement("delete from bytejta where xid = ?");
			for (int i = 0; i < xids.length; i++) {
				stmt.setString(1, xidArray[i]);
				stmt.addBatch();
			}
			stmt.executeBatch();
			conn.commit();
		} catch (Exception ex) {
			try {
				conn.rollback();
			} catch (Exception sqlEx) {
				logger.error("Error occurred while rolling back local resources.", sqlEx);
			}

			boolean tableExists = false;
			try {
				tableExists = this.isTableExists(conn);
			} catch (Exception sqlEx) {
				logger.warn("Error occurred while forgeting local resources.", ex);
				throw new XAException(XAException.XAER_RMFAIL);
			}

			if (tableExists) {
				logger.error("Error occurred while forgetting resources.", ex);
				throw new XAException(XAException.XAER_RMERR);
			}
		} finally {
			this.setAutoCommitIfNecessary(conn, autoCommit);
			this.closeQuietly(stmt);
			this.closeQuietly(conn);
		}
	}

	public synchronized void forget(Xid xid) throws XAException {
		if (xid == null) {
			logger.warn("Error occurred while forgeting local-xa-resource: invalid xid.");
			return;
		}

		byte[] globalTransactionId = xid.getGlobalTransactionId();
		byte[] branchQualifier = xid.getBranchQualifier();

		String identifier = this.getIdentifier(globalTransactionId, branchQualifier);

		Connection conn = null;
		PreparedStatement stmt = null;
		Boolean autoCommit = null;
		try {
			conn = this.dataSource.getConnection();
			autoCommit = conn.getAutoCommit();
			conn.setAutoCommit(false);
			stmt = conn.prepareStatement("delete from bytejta where xid = ?");
			stmt.setString(1, identifier);
			stmt.executeUpdate();
			conn.commit();
		} catch (Exception ex) {
			try {
				conn.rollback();
			} catch (Exception sqlEx) {
				logger.error("Error occurred while rolling back local resources.", sqlEx);
			}

			boolean tableExists = false;
			try {
				tableExists = this.isTableExists(conn);
			} catch (Exception sqlEx) {
				logger.warn("Error occurred while forgeting local-xa-resource.", ex);
				throw new XAException(XAException.XAER_RMFAIL);
			}

			if (tableExists) {
				logger.warn("Error occurred while forgeting local-xa-resource.", ex);
				throw new XAException(XAException.XAER_RMERR);
			}
		} finally {
			this.setAutoCommitIfNecessary(conn, autoCommit);
			this.closeQuietly(stmt);
			this.closeQuietly(conn);
		}
	}

	private void setAutoCommitIfNecessary(Connection conn, Boolean autoCommit) {
		if (autoCommit != null) {
			try {
				conn.setAutoCommit(autoCommit);
			} catch (SQLException sqlEx) {
				logger.error("Error occurred while configuring attribute 'autoCommit'.", sqlEx);
			}
		} // end-if (autoCommit != null)
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

}
