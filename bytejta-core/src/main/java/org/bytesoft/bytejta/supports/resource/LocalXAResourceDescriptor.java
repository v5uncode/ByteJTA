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
package org.bytesoft.bytejta.supports.resource;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.commons.lang3.StringUtils;
import org.bytesoft.bytejta.supports.jdbc.LocalXAResource;
import org.bytesoft.bytejta.supports.jdbc.RecoveredResource;
import org.bytesoft.transaction.supports.resource.XAResourceDescriptor;

public class LocalXAResourceDescriptor implements XAResourceDescriptor {

	private String identifier;
	private LocalXAResource delegate;
	private boolean loggingRequired;

	public boolean isTransactionCommitted(Xid xid) throws IllegalStateException {
		try {
			if (RecoveredResource.class.isInstance(this.delegate)) {
				((RecoveredResource) this.delegate).recoverable(xid);
			} else {
				((LocalXAResource) this.delegate).recoverable(xid);
			}
			return true;
		} catch (XAException ex) {
			switch (ex.errorCode) {
			case XAException.XAER_NOTA:
				return false;
			default:
				throw new IllegalStateException(ex);
			}
		}
	}

	public String toString() {
		return String.format("local-xa-resource[%s]", this.identifier);
	}

	public void setTransactionTimeoutQuietly(int timeout) {
		try {
			this.delegate.setTransactionTimeout(timeout);
		} catch (Exception ex) {
			return;
		}
	}

	public void commit(Xid arg0, boolean arg1) throws XAException {
		if (this.delegate == null) {
			return;
		}
		delegate.commit(arg0, this.loggingRequired); // onePhaseCommit is unnecessary.
	}

	public void end(Xid arg0, int arg1) throws XAException {
		if (this.delegate == null) {
			return;
		}
		delegate.end(arg0, arg1);
	}

	public void forget(Xid arg0) throws XAException {
		if (this.delegate == null) {
			return;
		}
		delegate.forget(arg0);
	}

	public int getTransactionTimeout() throws XAException {
		if (this.delegate == null) {
			return 0;
		}
		return delegate.getTransactionTimeout();
	}

	public boolean isSameRM(XAResource xares) throws XAException {
		if (this.delegate == null) {
			return false;
		}

		if (LocalXAResourceDescriptor.class.isInstance(xares)) {
			LocalXAResourceDescriptor that = (LocalXAResourceDescriptor) xares;
			boolean identifierEquals = StringUtils.equals(this.identifier, that.identifier);
			boolean xaResourceEquals = this.delegate.isSameRM(that.delegate); // this.delegate != null
			return identifierEquals && xaResourceEquals;
		} else {
			return delegate.isSameRM(xares);
		}

	}

	public int prepare(Xid arg0) throws XAException {
		if (this.delegate == null) {
			return XAResource.XA_RDONLY;
		}
		return delegate.prepare(arg0);
	}

	public Xid[] recover(int arg0) throws XAException {
		if (this.delegate == null) {
			return new Xid[0];
		}
		return delegate.recover(arg0);
	}

	public void rollback(Xid arg0) throws XAException {
		if (this.delegate == null) {
			return;
		}
		delegate.rollback(arg0);
	}

	public boolean setTransactionTimeout(int arg0) throws XAException {
		if (this.delegate == null) {
			return false;
		}
		return delegate.setTransactionTimeout(arg0);
	}

	public void start(Xid arg0, int arg1) throws XAException {
		if (this.delegate == null) {
			return;
		}
		delegate.start(arg0, arg1);
	}

	public boolean isLoggingRequired() {
		return loggingRequired;
	}

	public void setLoggingRequired(boolean loggingRequired) {
		this.loggingRequired = loggingRequired;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public XAResource getDelegate() {
		return delegate;
	}

	public void setDelegate(LocalXAResource delegate) {
		this.delegate = delegate;
	}

}
