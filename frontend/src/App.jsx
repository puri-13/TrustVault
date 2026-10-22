
import { useCallback, useEffect, useState } from "react";
import "./App.css";

async function apiRequest(path, username, password, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("Authorization", `Basic ${btoa(`${username}:${password}`)}`);

  const response = await fetch(path, { ...options, headers });

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    if (response.status === 401) {
      throw new Error("Invalid username or password, or session expired.");
    }
    if (response.status === 403) {
      throw new Error("You don't have permission to perform this action.");
    }
    throw new Error(body.error || `Request failed (${response.status}).`);
  }

  if (response.status === 204) return null;
  return response.json();
}

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString() : "—";
}

function App() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [authenticatedUser, setAuthenticatedUser] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [sharedDocuments, setSharedDocuments] = useState([]);
  const [search, setSearch] = useState("");
  const [activePage, setActivePage] = useState("Documents");
  const [loading, setLoading] = useState(false);
  const [sharedLoading, setSharedLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [showUpload, setShowUpload] = useState(false);
  const [documentName, setDocumentName] = useState("");
  const [category, setCategory] = useState("General");
  const [file, setFile] = useState(null);

  const [selectedDoc, setSelectedDoc] = useState(null);
  const [permissions, setPermissions] = useState([]);
  const [targetUsername, setTargetUsername] = useState("");
  const [permissionLevel, setPermissionLevel] = useState("READ");
  const [sharingLoading, setSharingLoading] = useState(false);

  const loadDocuments = useCallback(async (user, pass) => {
    setLoading(true);
    setError("");

    try {
      const data = await apiRequest("/api/documents", user, pass);
      setDocuments(Array.isArray(data) ? data : []);
      setAuthenticatedUser(user);
      setPassword(pass);
    } catch (err) {
      setError(err.message);
      setAuthenticatedUser(null);
      setPassword("");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadSharedDocuments = useCallback(async (user, pass) => {
    setSharedLoading(true);
    setError("");

    try {
      const data = await apiRequest(
        "/api/documents/shared-with-me",
        user,
        pass
      );
      setSharedDocuments(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message);
      setSharedDocuments([]);
    } finally {
      setSharedLoading(false);
    }
  }, []);

  useEffect(() => {
    if (authenticatedUser) {
      loadDocuments(authenticatedUser, password);
    }
    // Initial login is handled by handleLogin.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleLogin(event) {
    event.preventDefault();
    setSuccess("");
    await loadDocuments(username.trim(), password);
  }

  function handleLogout() {
    setAuthenticatedUser(null);
    setPassword("");
    setUsername("");
    setDocuments([]);
    setSharedDocuments([]);
    setSearch("");
    setError("");
    setSuccess("");
    setActivePage("Documents");
    setSelectedDoc(null);
  }

  async function handlePageChange(page) {
    setActivePage(page);
    setSearch("");
    setError("");
    setSuccess("");

    if (page === "Shared with me" && authenticatedUser) {
      await loadSharedDocuments(authenticatedUser, password);
    } else if (page === "Documents" && authenticatedUser) {
      await loadDocuments(authenticatedUser, password);
    }
  }

  async function handleUpload(event) {
    event.preventDefault();

    if (!file) {
      setError("Please choose a file to upload.");
      return;
    }

    const formData = new FormData();
    formData.append("name", documentName.trim());
    formData.append("category", category.trim());
    formData.append("file", file);

    setUploading(true);
    setError("");
    setSuccess("");

    try {
      await apiRequest("/api/documents", authenticatedUser, password, {
        method: "POST",
        body: formData,
      });

      setSuccess("Document uploaded successfully.");
      setShowUpload(false);
      setDocumentName("");
      setCategory("General");
      setFile(null);
      await loadDocuments(authenticatedUser, password);
    } catch (err) {
      setError(err.message);
    } finally {
      setUploading(false);
    }
  }

  async function openSharing(doc) {
    setSelectedDoc(doc);
    setPermissions([]);
    setTargetUsername("");
    setPermissionLevel("READ");
    setError("");
    setSuccess("");
    setSharingLoading(true);

    try {
      const data = await apiRequest(
        `/api/documents/${doc.id}/permissions`,
        authenticatedUser,
        password
      );
      setPermissions(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message);
      setSelectedDoc(null);
    } finally {
      setSharingLoading(false);
    }
  }

  async function grantAccess(event) {
    event.preventDefault();
    if (!selectedDoc || !targetUsername.trim()) return;

    setSharingLoading(true);
    setError("");
    setSuccess("");

    try {
      const result = await apiRequest(
        `/api/documents/${selectedDoc.id}/permissions`,
        authenticatedUser,
        password,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: targetUsername.trim(),
            permission: permissionLevel,
          }),
        }
      );

      setPermissions((current) => [
        ...current.filter((item) => item.username !== result.username),
        result,
      ]);
      setTargetUsername("");
      setSuccess(`Access granted to ${result.username} (${result.permission}).`);

      // Refresh the shared list so changes appear there too.
      await loadSharedDocuments(authenticatedUser, password);
    } catch (err) {
      setError(err.message);
    } finally {
      setSharingLoading(false);
    }
  }

  async function revokeAccess(user) {
    if (!selectedDoc) return;

    setSharingLoading(true);
    setError("");
    setSuccess("");

    try {
      await apiRequest(
        `/api/documents/${selectedDoc.id}/permissions?username=${encodeURIComponent(user)}`,
        authenticatedUser,
        password,
        { method: "DELETE" }
      );

      setPermissions((current) =>
        current.filter((item) => item.username !== user)
      );
      setSuccess(`Access revoked for ${user}.`);
      await loadSharedDocuments(authenticatedUser, password);
    } catch (err) {
      setError(err.message);
    } finally {
      setSharingLoading(false);
    }
  }

  const filteredDocuments = documents.filter((doc) =>
    `${doc.name} ${doc.category} ${doc.owner}`
      .toLowerCase()
      .includes(search.toLowerCase())
  );

  const filteredSharedDocuments = sharedDocuments.filter((doc) =>
    `${doc.name} ${doc.category} ${doc.owner} ${doc.permission}`
      .toLowerCase()
      .includes(search.toLowerCase())
  );

  const menuItems = [
    { name: "Overview", icon: "▦" },
    { name: "Documents", icon: "▤" },
    { name: "Shared with me", icon: "⇄" },
    { name: "Activity logs", icon: "◷" },
    { name: "Settings", icon: "⚙" },
  ];

  if (!authenticatedUser) {
    return (
      <div className="login-page">
        <form className="login-card" onSubmit={handleLogin}>
          <div className="login-brand-icon">T</div>
          <h1>Welcome to TrustVault</h1>
          <p className="login-description">
            Sign in to access your secure document workspace.
          </p>

          {error && <div className="message error-message">{error}</div>}

          <label htmlFor="username">Username</label>
          <input
            id="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder="Enter your username"
            autoComplete="username"
            required
          />

          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="Enter your password"
            autoComplete="current-password"
            required
          />

          <button className="primary-button login-button" disabled={loading}>
            {loading ? "Signing in..." : "Sign in securely →"}
          </button>

          <div className="login-security">♧ Protected document workspace</div>
        </form>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon">T</div>
          <div>
            <h2>TrustVault</h2>
            <span>SECURE WORKSPACE</span>
          </div>
        </div>

        <div className="workspace-label">WORKSPACE</div>
        <nav className="navigation">
          {menuItems.map((item) => (
            <button
              key={item.name}
              className={`nav-item ${activePage === item.name ? "active" : ""}`}
              onClick={() => handlePageChange(item.name)}
            >
              <span className="nav-icon">{item.icon}</span>
              {item.name}
              {item.name === "Documents" && (
                <span className="nav-count">{documents.length}</span>
              )}
              {item.name === "Shared with me" && (
                <span className="nav-count">{sharedDocuments.length}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="security-card">
            <div className="security-symbol">✓</div>
            <strong>Your files, protected.</strong>
            <p>Access-controlled document storage.</p>
            <div className="security-status"><span /> Authenticated workspace</div>
          </div>
          <div className="profile">
            <div className="avatar">{authenticatedUser.charAt(0).toUpperCase()}</div>
            <div className="profile-info">
              <strong>{authenticatedUser}</strong>
              <span>Signed in</span>
            </div>
            <button className="logout-button" onClick={handleLogout}>Log out</button>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace <span>/</span> <strong>{activePage}</strong>
          </div>
          <div className="topbar-right">
            <span className="secure-indicator"><span /> Secure workspace</span>
            <div className="top-avatar">{authenticatedUser.charAt(0).toUpperCase()}</div>
          </div>
        </header>

        <section className="page-content">
          <div className="welcome-row">
            <div>
              <div className="eyebrow">YOUR PRIVATE WORKSPACE</div>
              <h1>{activePage}</h1>
              <p className="page-description">
                {activePage === "Documents"
                  ? "Organize, find, and securely manage your documents."
                  : activePage === "Shared with me"
                    ? "Documents other users have shared with your account."
                    : `Your ${activePage.toLowerCase()} workspace.`}
              </p>
            </div>

            {activePage === "Documents" && (
              <button className="primary-button" onClick={() => {
                setError("");
                setSuccess("");
                setShowUpload(true);
              }}>
                <span>＋</span> Upload document
              </button>
            )}
          </div>

          {error && <div className="message error-message">{error}</div>}
          {success && <div className="message success-message">{success}</div>}

          {activePage === "Documents" ? (
            <>
              <div className="stats-grid">
                <div className="stat-card">
                  <div className="stat-top">
                    <span>My documents</span>
                    <div className="stat-icon blue">▤</div>
                  </div>
                  <div className="stat-number">{documents.length}</div>
                  <div className="stat-foot">Documents returned by the server</div>
                </div>
                <div className="stat-card">
                  <div className="stat-top">
                    <span>Search results</span>
                    <div className="stat-icon purple">⌕</div>
                  </div>
                  <div className="stat-number">{filteredDocuments.length}</div>
                  <div className="stat-foot">Matching your search</div>
                </div>
                <div className="stat-card">
                  <div className="stat-top">
                    <span>Signed in as</span>
                    <div className="stat-icon green">✓</div>
                  </div>
                  <div className="stat-number stat-user">{authenticatedUser}</div>
                  <div className="stat-foot">Identity authenticated by the backend</div>
                </div>
              </div>

              <section className="documents-panel">
                <div className="panel-heading">
                  <div>
                    <h2>My documents</h2>
                    <p>Live data from your TrustVault backend</p>
                  </div>
                  <button className="filter-button" onClick={() => {
                    setSearch("");
                    loadDocuments(authenticatedUser, password);
                  }}>↻ Refresh</button>
                </div>

                <div className="table-toolbar">
                  <div className="search-box">
                    <span>⌕</span>
                    <input
                      value={search}
                      onChange={(event) => setSearch(event.target.value)}
                      placeholder="Search documents..."
                      aria-label="Search documents"
                    />
                  </div>
                  <span className="results-count">{filteredDocuments.length} documents</span>
                </div>

                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>DOCUMENT NAME</th>
                        <th>CATEGORY</th>
                        <th>OWNER</th>
                        <th>DATE ADDED</th>
                        <th>ACCESS</th>
                        <th>SHARING</th>
                      </tr>
                    </thead>
                    <tbody>
                      {loading ? (
                        <tr><td colSpan="6" className="empty-state">Loading documents...</td></tr>
                      ) : filteredDocuments.map((doc) => {
                        const extension = doc.name?.toLowerCase().endsWith(".pdf") ? "PDF" : "FILE";

                        return (
                          <tr key={doc.id}>
                            <td>
                              <div className="document-cell">
                                <div className={`file-icon ${extension.toLowerCase()}`}>{extension}</div>
                                <div>
                                  <strong>{doc.name}</strong>
                                  <span>Document ID: {doc.id}</span>
                                </div>
                              </div>
                            </td>
                            <td><span className="category-pill">{doc.category}</span></td>
                            <td>
                              <div className="owner-cell">
                                <span>{(doc.owner || "?").charAt(0).toUpperCase()}</span>
                                {doc.owner}
                              </div>
                            </td>
                            <td className="date-cell">{formatDate(doc.uploadedAt)}</td>
                            <td><span className="access-pill"><span /> Authorized</span></td>
                            <td>
                              <button className="filter-button" onClick={() => openSharing(doc)}>
                                Manage access
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                      {!loading && filteredDocuments.length === 0 && (
                        <tr>
                          <td colSpan="6" className="empty-state">
                            {documents.length === 0
                              ? "No documents found for this account. Upload a file to get started."
                              : "No matching documents. Try another search."}
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="panel-footer">
                  <span>Showing {filteredDocuments.length} of {documents.length} server documents</span>
                  <span className="footer-lock">♧ Protected workspace</span>
                </div>
              </section>
            </>
          ) : activePage === "Shared with me" ? (
            <section className="documents-panel">
              <div className="panel-heading">
                <div>
                  <h2>Shared documents</h2>
                  <p>Documents shared with {authenticatedUser}</p>
                </div>
                <button
                  className="filter-button"
                  disabled={sharedLoading}
                  onClick={() => loadSharedDocuments(authenticatedUser, password)}
                >
                  {sharedLoading ? "Refreshing..." : "↻ Refresh"}
                </button>
              </div>

              <div className="table-toolbar">
                <div className="search-box">
                  <span>⌕</span>
                  <input
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder="Search shared documents..."
                    aria-label="Search shared documents"
                  />
                </div>
                <span className="results-count">
                  {filteredSharedDocuments.length} documents
                </span>
              </div>

              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>DOCUMENT NAME</th>
                      <th>CATEGORY</th>
                      <th>OWNER</th>
                      <th>DATE ADDED</th>
                      <th>YOUR PERMISSION</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sharedLoading ? (
                      <tr>
                        <td colSpan="5" className="empty-state">
                          Loading shared documents...
                        </td>
                      </tr>
                    ) : filteredSharedDocuments.map((doc) => (
                      <tr key={doc.id}>
                        <td>
                          <div className="document-cell">
                            <div className="file-icon">FILE</div>
                            <div>
                              <strong>{doc.name}</strong>
                              <span>Document ID: {doc.id}</span>
                            </div>
                          </div>
                        </td>
                        <td><span className="category-pill">{doc.category}</span></td>
                        <td>
                          <div className="owner-cell">
                            <span>{(doc.owner || "?").charAt(0).toUpperCase()}</span>
                            {doc.owner}
                          </div>
                        </td>
                        <td className="date-cell">{formatDate(doc.uploadedAt)}</td>
                        <td>
                          <span className="category-pill">
                            {doc.permission || "READ"}
                          </span>
                        </td>
                      </tr>
                    ))}
                    {!sharedLoading && filteredSharedDocuments.length === 0 && (
                      <tr>
                        <td colSpan="5" className="empty-state">
                          {sharedDocuments.length === 0
                            ? "No documents have been shared with you yet."
                            : "No matching documents. Try another search."}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              <div className="panel-footer">
                <span>
                  Showing {filteredSharedDocuments.length} of {sharedDocuments.length} shared documents
                </span>
                <span className="footer-lock">♧ Access-controlled documents</span>
              </div>
            </section>
          ) : (
            <section className="documents-panel placeholder-panel">
              <h2>{activePage}</h2>
              <p>This section's backend integration is not implemented yet.</p>
            </section>
          )}

          <footer className="page-footer">
            <span>© 2026 TrustVault</span>
            <span>Secure document management</span>
          </footer>
        </section>
      </main>

      {showUpload && (
        <div className="modal-backdrop" onClick={(event) => {
          if (event.target === event.currentTarget && !uploading) setShowUpload(false);
        }}>
          <form className="upload-modal" onSubmit={handleUpload}>
            <div className="modal-heading">
              <div>
                <h2>Upload document</h2>
                <p>Add a document to your secure workspace.</p>
              </div>
              <button type="button" className="modal-close" disabled={uploading} onClick={() => setShowUpload(false)}>×</button>
            </div>

            <label htmlFor="documentName">Document name</label>
            <input
              id="documentName"
              value={documentName}
              onChange={(event) => setDocumentName(event.target.value)}
              maxLength={200}
              placeholder="e.g. Project Requirements"
              required
            />

            <label htmlFor="category">Category</label>
            <input
              id="category"
              value={category}
              onChange={(event) => setCategory(event.target.value)}
              maxLength={100}
              required
            />

            <label htmlFor="documentFile">Choose file</label>
            <input
              id="documentFile"
              type="file"
              onChange={(event) => setFile(event.target.files?.[0] || null)}
              accept=".pdf,.txt,.png,.jpg,.jpeg,.docx"
              required
            />
            <p className="upload-hint">Allowed types: PDF, TXT, PNG, JPG, JPEG, DOCX. Maximum 10 MB.</p>

            <div className="modal-actions">
              <button type="button" className="filter-button" disabled={uploading} onClick={() => setShowUpload(false)}>Cancel</button>
              <button className="primary-button" disabled={uploading}>
                {uploading ? "Uploading..." : "Upload securely"}
              </button>
            </div>
          </form>
        </div>
      )}

      {selectedDoc && (
        <div
          className="modal-backdrop"
          onClick={(event) => {
            if (event.target === event.currentTarget && !sharingLoading) {
              setSelectedDoc(null);
            }
          }}
        >
          <section className="upload-modal">
            <div className="modal-heading">
              <div>
                <h2>Manage document access</h2>
                <p>{selectedDoc.name}</p>
              </div>
              <button
                type="button"
                className="modal-close"
                disabled={sharingLoading}
                onClick={() => setSelectedDoc(null)}
              >×</button>
            </div>

            <form onSubmit={grantAccess}>
              <label htmlFor="shareUsername">Username</label>
              <input
                id="shareUsername"
                value={targetUsername}
                onChange={(event) => setTargetUsername(event.target.value)}
                placeholder="Enter an existing username"
                maxLength={100}
                required
              />

              <label htmlFor="sharePermission">Permission level</label>
              <select
                id="sharePermission"
                value={permissionLevel}
                onChange={(event) => setPermissionLevel(event.target.value)}
              >
                <option value="READ">READ — View document</option>
                <option value="EDIT">EDIT — Edit access</option>
              </select>

              <div className="modal-actions">
                <button className="primary-button" disabled={sharingLoading}>
                  {sharingLoading ? "Please wait..." : "Grant access"}
                </button>
              </div>
            </form>

            <div className="sharing-list">
              <h3>Users with access</h3>

              {sharingLoading ? (
                <p>Loading permissions...</p>
              ) : permissions.length === 0 ? (
                <p>No users have been granted access yet.</p>
              ) : (
                permissions.map((item) => (
                  <div className="sharing-user" key={item.username}>
                    <div>
                      <strong>{item.username}</strong>
                      <p>
                        {item.permission} ·{" "}
                        {item.grantedAt
                          ? new Date(item.grantedAt).toLocaleString()
                          : "Date unavailable"}
                      </p>
                    </div>
                    <button
                      type="button"
                      className="filter-button"
                      disabled={sharingLoading}
                      onClick={() => revokeAccess(item.username)}
                    >
                      Revoke
                    </button>
                  </div>
                ))
              )}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

export default App;
