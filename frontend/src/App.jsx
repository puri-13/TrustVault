import { useCallback, useEffect, useState } from "react";
import "./App.css";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "");

function apiUrl(path) {
  return `${API_BASE_URL}${path}`;
}

function basicAuthorization(username, password) {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("");
  return `Basic ${btoa(binary)}`;
}

async function apiRequest(path, username, password, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("Authorization", basicAuthorization(username, password));

  const response = await fetch(apiUrl(path), { ...options, headers });

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));

    if (response.status === 401) {
      throw new Error("Invalid username or password, or session expired.");
    }

    if (response.status === 403) {
      throw new Error("You don't have permission to perform this action.");
    }

    throw new Error(
      body.error ||
        `Request failed (${response.status}).`
    );
  }

  if (response.status === 204) return null;
  return response.json();
}

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleDateString();
}

function App() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [authenticatedUser, setAuthenticatedUser] = useState(null);
  const [authenticatedRole, setAuthenticatedRole] = useState(null);

  // Authentication and registration state
  const [showRegistration, setShowRegistration] = useState(false);
  const [registering, setRegistering] = useState(false);

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

  const [auditLogs, setAuditLogs] = useState([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditUnavailable, setAuditUnavailable] = useState(false);

  const canUpload = authenticatedRole === "OWNER";

  const loadDocuments = useCallback(async (user, pass) => {
    setLoading(true);
    setError("");

    try {
      const data = await apiRequest("/api/documents", user, pass);
      const currentUser = await apiRequest("/api/auth/me", user, pass);

      setDocuments(Array.isArray(data) ? data : []);
      setAuthenticatedUser(currentUser.username);
      setAuthenticatedRole(currentUser.role);
      setPassword(pass);
    } catch (err) {
      setError(err.message);
      setAuthenticatedUser(null);
      setAuthenticatedRole(null);
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

  const loadAuditLogs = useCallback(async (user, pass) => {
    setAuditLoading(true);
    setAuditUnavailable(false);
    setAuditLogs([]);
    setError("");

    try {
      const data = await apiRequest("/api/audit", user, pass);
      setAuditLogs(Array.isArray(data) ? data : []);
    } catch (err) {
      if (
        err.message.toLowerCase().includes("permission") ||
        err.message.includes("403")
      ) {
        setAuditUnavailable(true);
      } else {
        setError(err.message);
      }
    } finally {
      setAuditLoading(false);
    }
  }, []);

  useEffect(() => {
    // Login is handled by handleLogin.
  }, []);

  async function handleLogin(event) {
    event.preventDefault();

    setError("");
    setSuccess("");

    const normalizedUsername = username.trim();

    if (!normalizedUsername || !password) {
      setError("Please enter your username and password.");
      return;
    }

    await loadDocuments(normalizedUsername, password);
  }

  async function handleRegister(event) {
    event.preventDefault();

    setError("");
    setSuccess("");

    const normalizedUsername = username.trim();

    if (normalizedUsername.length < 3 || normalizedUsername.length > 50) {
      setError("Username must contain between 3 and 50 characters.");
      return;
    }

    if (password.length < 8 || password.length > 72) {
      setError("Password must contain between 8 and 72 characters.");
      return;
    }

    setRegistering(true);

    try {
      const response = await fetch(apiUrl("/api/auth/register"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          username: normalizedUsername,
          password,
        }),
      });

      const body = await response.json().catch(() => ({}));

      if (!response.ok) {
        const validationMessage = Array.isArray(body.errors)
          ? body.errors
              .map((item) => item.defaultMessage)
              .filter(Boolean)
              .join(" ")
          : "";

        if (
          response.status === 400 &&
          (body.error || validationMessage)
        ) {
          throw new Error(body.error || validationMessage);
        }

        if (response.status === 409) {
          throw new Error(
            "This username already exists. Please choose another."
          );
        }

        throw new Error(
          body.error ||
            validationMessage ||
            `Registration failed (${response.status}). Please try again.`
        );
      }

      setUsername(body.username || normalizedUsername);
      setPassword("");
      setShowRegistration(false);
      setError("");
      setSuccess(
        "Account created successfully! Sign in with your new password."
      );
    } catch (err) {
      setError(
        err.message ||
          "Unable to create your account. Please try again."
      );
    } finally {
      setRegistering(false);
    }
  }

  function handleLogout() {
    setAuthenticatedUser(null);
    setAuthenticatedRole(null);
    setPassword("");
    setUsername("");
    setDocuments([]);
    setSharedDocuments([]);
    setAuditLogs([]);
    setSearch("");
    setError("");
    setSuccess("");
    setActivePage("Documents");
    setSelectedDoc(null);
    setPermissions([]);
    setAuditUnavailable(false);
    setShowUpload(false);
    setDocumentName("");
    setCategory("General");
    setFile(null);
    setShowRegistration(false);
  }

  async function handlePageChange(page) {
    setActivePage(page);
    setSearch("");
    setError("");
    setSuccess("");

    if (!authenticatedUser) return;

    if (page === "Shared with me") {
      await loadSharedDocuments(authenticatedUser, password);
    } else if (page === "Documents" || page === "Overview") {
      await loadDocuments(authenticatedUser, password);
    } else if (page === "Activity logs") {
      await loadAuditLogs(authenticatedUser, password);
    }
  }

  async function handleUpload(event) {
    event.preventDefault();

    if (!canUpload) {
      setError("Your account is not allowed to upload documents.");
      setShowUpload(false);
      return;
    }

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

  async function downloadDocument(doc) {
    setError("");

    try {
      const response = await fetch(apiUrl(`/api/documents/${doc.id}/download`), {
        headers: {
          Authorization: basicAuthorization(authenticatedUser, password),
        },
      });

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error("Invalid username or password, or session expired.");
        }
        if (response.status === 403) {
          throw new Error("You don't have permission to download this document.");
        }
        if (response.status === 404) {
          throw new Error("Document not found or access has been revoked.");
        }
        throw new Error(`Download failed (${response.status}).`);
      }

      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement("a");
      const filename = response.headers
        .get("Content-Disposition")
        ?.match(/filename="([^"]+)"/i)?.[1];
      link.href = objectUrl;
      link.download = filename || doc.name || "document";
      document.body.append(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    } catch (err) {
      setError(err.message || "Unable to download this document.");
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
          headers: {
            "Content-Type": "application/json",
          },
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
      setSuccess(
        `Access granted to ${result.username} (${result.permission}).`
      );

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
        {
          method: "DELETE",
        }
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
    `${doc.name || ""} ${doc.category || ""} ${doc.owner || ""}`
      .toLowerCase()
      .includes(search.toLowerCase())
  );

  const filteredSharedDocuments = sharedDocuments.filter((doc) =>
    `${doc.name || ""} ${doc.category || ""} ${doc.owner || ""} ${doc.permission || ""}`
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

  // --------------------------------------------------
  // LOGIN AND REGISTRATION SCREEN
  // --------------------------------------------------

  if (!authenticatedUser) {
    return (
      <div className="login-page">
        <form
          className="login-card"
          onSubmit={showRegistration ? handleRegister : handleLogin}
        >
          <div className="login-brand-icon">T</div>

          <h1>
            {showRegistration
              ? "Create your TrustVault account"
              : "Welcome to TrustVault"}
          </h1>

          <p className="login-description">
            {showRegistration
              ? "Create a secure account for your private document workspace."
              : "Sign in to access your secure document workspace."}
          </p>

          {error && (
            <div className="message error-message" role="alert">
              {error}
            </div>
          )}

          {success && (
            <div className="message success-message" role="status">
              {success}
            </div>
          )}

          <label htmlFor="username">Username</label>

          <input
            id="username"
            type="text"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder="Enter your username"
            autoComplete="username"
            minLength={showRegistration ? 3 : undefined}
            maxLength={50}
            required
          />

          <label htmlFor="password">Password</label>

          <input
            id="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder={
              showRegistration
                ? "Create a password (8–72 characters)"
                : "Enter your password"
            }
            autoComplete={
              showRegistration ? "new-password" : "current-password"
            }
            minLength={showRegistration ? 8 : undefined}
            maxLength={72}
            required
          />

          <button
            type="submit"
            className="primary-button login-button"
            disabled={loading || registering}
          >
            {registering
              ? "Creating account..."
              : loading
                ? "Signing in..."
                : showRegistration
                  ? "Create account →"
                  : "Sign in securely →"}
          </button>

          <p className="login-description">
            {showRegistration
              ? "Already have an account?"
              : "New to TrustVault?"}
          </p>

          <button
            type="button"
            className="filter-button"
            disabled={loading || registering}
            onClick={() => {
              setShowRegistration((current) => !current);
              setError("");
              setSuccess("");
              setPassword("");
            }}
          >
            {showRegistration
              ? "Back to sign in"
              : "Create a new account"}
          </button>

          <div className="login-security">
            ♧ Protected document workspace
          </div>
        </form>
      </div>
    );
  }

  // --------------------------------------------------
  // AUTHENTICATED APPLICATION DASHBOARD
  // --------------------------------------------------

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

            <div className="security-status">
              <span /> Authenticated workspace
            </div>
          </div>

          <div className="profile">
            <div className="avatar">
              {authenticatedUser.charAt(0).toUpperCase()}
            </div>

            <div className="profile-info">
              <strong>{authenticatedUser}</strong>
              <span>Signed in</span>
            </div>

            <button className="logout-button" onClick={handleLogout}>
              Log out
            </button>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace <span>/</span> <strong>{activePage}</strong>
          </div>

          <div className="topbar-right">
            <span className="secure-indicator">
              <span /> Secure workspace
            </span>

            <div className="top-avatar">
              {authenticatedUser.charAt(0).toUpperCase()}
            </div>
          </div>
        </header>

        <section className="page-content">
          <div className="welcome-row">
            <div>
              <div className="eyebrow">YOUR PRIVATE WORKSPACE</div>
              <h1>{activePage}</h1>

              <p className="page-description">
                {activePage === "Overview"
                  ? "Your secure document workspace at a glance."
                  : activePage === "Documents"
                    ? "Organize, find, and securely manage your documents."
                    : activePage === "Shared with me"
                      ? "Documents other users have shared with your account."
                      : activePage === "Activity logs"
                        ? "Review recorded document and security activity."
                        : "Manage your account and workspace security."}
              </p>
            </div>

            {activePage === "Documents" && canUpload && (
              <button
                className="primary-button"
                onClick={() => {
                  setError("");
                  setSuccess("");
                  setShowUpload(true);
                }}
              >
                <span>＋</span> Upload document
              </button>
            )}
          </div>

          {error && (
            <div className="message error-message" role="alert">
              {error}
            </div>
          )}

          {success && (
            <div className="message success-message" role="status">
              {success}
            </div>
          )}

          {/* OVERVIEW PAGE */}

          {activePage === "Overview" && (
            <>
              <div className="stats-grid">
                <div className="stat-card">
                  <div className="stat-top">
                    <span>My documents</span>
                    <div className="stat-icon blue">▤</div>
                  </div>

                  <div className="stat-number">{documents.length}</div>

                  <div className="stat-foot">
                    Documents owned by your account
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-top">
                    <span>Shared with me</span>
                    <div className="stat-icon purple">⇄</div>
                  </div>

                  <div className="stat-number">{sharedDocuments.length}</div>

                  <div className="stat-foot">
                    Documents shared with your account
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-top">
                    <span>Signed in as</span>
                    <div className="stat-icon green">✓</div>
                  </div>

                  <div className="stat-number stat-user">
                    {authenticatedUser}
                  </div>

                  <div className="stat-foot">
                    Authenticated workspace account
                  </div>
                </div>
              </div>

              <section className="documents-panel">
                <div className="panel-heading">
                  <div>
                    <h2>Recent documents</h2>
                    <p>Your latest documents from the server.</p>
                  </div>

                  <button
                    className="filter-button"
                    onClick={() => handlePageChange("Documents")}
                  >
                    View all documents →
                  </button>
                </div>

                {documents.length === 0 ? (
                  <div className="empty-state">
                    <h3>No documents yet</h3>

                    <p>
                      Upload a document to start using your secure workspace.
                    </p>

                    {canUpload && (
                      <button
                        className="primary-button"
                        onClick={() => {
                          setError("");
                          setSuccess("");
                          setActivePage("Documents");
                          setShowUpload(true);
                        }}
                      >
                        ＋ Upload document
                      </button>
                    )}
                  </div>
                ) : (
                  <div className="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>DOCUMENT NAME</th>
                          <th>CATEGORY</th>
                          <th>DATE ADDED</th>
                        </tr>
                      </thead>

                      <tbody>
                        {[...documents]
                          .sort(
                            (a, b) =>
                              new Date(b.uploadedAt || 0) -
                              new Date(a.uploadedAt || 0)
                          )
                          .slice(0, 5)
                          .map((doc) => (
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

                              <td>
                                <span className="category-pill">
                                  {doc.category || "General"}
                                </span>
                              </td>

                              <td className="date-cell">
                                {formatDate(doc.uploadedAt)}
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                )}

                <div className="panel-footer">
                  <span>{documents.length} total documents</span>
                  <span className="footer-lock">
                    ♧ Protected workspace
                  </span>
                </div>
              </section>
            </>
          )}

          {/* DOCUMENTS PAGE */}

          {activePage === "Documents" && (
            <>
              <div className="stats-grid">
                <div className="stat-card">
                  <div className="stat-top">
                    <span>My documents</span>
                    <div className="stat-icon blue">▤</div>
                  </div>

                  <div className="stat-number">{documents.length}</div>

                  <div className="stat-foot">
                    Documents returned by the server
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-top">
                    <span>Search results</span>
                    <div className="stat-icon purple">⌕</div>
                  </div>

                  <div className="stat-number">
                    {filteredDocuments.length}
                  </div>

                  <div className="stat-foot">
                    Matching your search
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-top">
                    <span>Signed in as</span>
                    <div className="stat-icon green">✓</div>
                  </div>

                  <div className="stat-number stat-user">
                    {authenticatedUser}
                  </div>

                  <div className="stat-foot">
                    Identity authenticated by the backend
                  </div>
                </div>
              </div>

              <section className="documents-panel">
                <div className="panel-heading">
                  <div>
                    <h2>My documents</h2>
                    <p>Live data from your TrustVault backend</p>
                  </div>

                  <button
                    className="filter-button"
                    onClick={() => {
                      setSearch("");
                      loadDocuments(authenticatedUser, password);
                    }}
                  >
                    ↻ Refresh
                  </button>
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

                  <span className="results-count">
                    {filteredDocuments.length} documents
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
                        <th>ACCESS</th>
                        <th>SHARING</th>
                        <th>DOWNLOAD</th>
                      </tr>
                    </thead>

                    <tbody>
                      {loading ? (
                        <tr>
                          <td colSpan="7" className="empty-state">
                            Loading documents...
                          </td>
                        </tr>
                      ) : (
                        filteredDocuments.map((doc) => {
                          const extension = doc.name
                            ?.toLowerCase()
                            .endsWith(".pdf")
                            ? "PDF"
                            : "FILE";

                          return (
                            <tr key={doc.id}>
                              <td>
                                <div className="document-cell">
                                  <div
                                    className={`file-icon ${extension.toLowerCase()}`}
                                  >
                                    {extension}
                                  </div>

                                  <div>
                                    <strong>{doc.name}</strong>
                                    <span>Document ID: {doc.id}</span>
                                  </div>
                                </div>
                              </td>

                              <td>
                                <span className="category-pill">
                                  {doc.category || "General"}
                                </span>
                              </td>

                              <td>
                                <div className="owner-cell">
                                  <span>
                                    {(doc.owner || "?")
                                      .charAt(0)
                                      .toUpperCase()}
                                  </span>

                                  {doc.owner || "—"}
                                </div>
                              </td>

                              <td className="date-cell">
                                {formatDate(doc.uploadedAt)}
                              </td>

                              <td>
                                <span className="access-pill">
                                  <span /> Authorized
                                </span>
                              </td>

                              <td>
                                <button
                                  className="filter-button"
                                  onClick={() => openSharing(doc)}
                                >
                                  Manage access
                                </button>
                              </td>

                              <td>
                                <button
                                  className="filter-button"
                                  onClick={() => downloadDocument(doc)}
                                >
                                  Download
                                </button>
                              </td>
                            </tr>
                          );
                        })
                      )}

                      {!loading && filteredDocuments.length === 0 && (
                        <tr>
                          <td colSpan="7" className="empty-state">
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
                  <span>
                    Showing {filteredDocuments.length} of{" "}
                    {documents.length} server documents
                  </span>

                  <span className="footer-lock">
                    ♧ Protected workspace
                  </span>
                </div>
              </section>
            </>
          )}

          {/* SHARED DOCUMENTS PAGE */}

          {activePage === "Shared with me" && (
            <section className="documents-panel">
              <div className="panel-heading">
                <div>
                  <h2>Shared documents</h2>
                  <p>Documents shared with {authenticatedUser}</p>
                </div>

                <button
                  className="filter-button"
                  disabled={sharedLoading}
                  onClick={() =>
                    loadSharedDocuments(authenticatedUser, password)
                  }
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
                      <th>DOWNLOAD</th>
                    </tr>
                  </thead>

                  <tbody>
                    {sharedLoading ? (
                      <tr>
                        <td colSpan="6" className="empty-state">
                          Loading shared documents...
                        </td>
                      </tr>
                    ) : (
                      filteredSharedDocuments.map((doc) => (
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

                          <td>
                            <span className="category-pill">
                              {doc.category || "General"}
                            </span>
                          </td>

                          <td>
                            <div className="owner-cell">
                              <span>
                                {(doc.owner || "?")
                                  .charAt(0)
                                  .toUpperCase()}
                              </span>

                              {doc.owner || "—"}
                            </div>
                          </td>

                          <td className="date-cell">
                            {formatDate(doc.uploadedAt)}
                          </td>

                          <td>
                            <span className="category-pill">
                              {doc.permission || "READ"}
                            </span>
                          </td>

                          <td>
                            <button
                              className="filter-button"
                              onClick={() => downloadDocument(doc)}
                            >
                              Download
                            </button>
                          </td>
                        </tr>
                      ))
                    )}

                    {!sharedLoading &&
                      filteredSharedDocuments.length === 0 && (
                        <tr>
                          <td colSpan="6" className="empty-state">
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
                  Showing {filteredSharedDocuments.length} of{" "}
                  {sharedDocuments.length} shared documents
                </span>

                <span className="footer-lock">
                  ♧ Access-controlled documents
                </span>
              </div>
            </section>
          )}

          {/* ACTIVITY LOGS PAGE */}

          {activePage === "Activity logs" && (
            <section className="documents-panel">
              <div className="panel-heading">
                <div>
                  <h2>Security activity</h2>
                  <p>
                    Audit events available to authorized administrators.
                  </p>
                </div>

                <button
                  className="filter-button"
                  disabled={auditLoading}
                  onClick={() =>
                    loadAuditLogs(authenticatedUser, password)
                  }
                >
                  {auditLoading ? "Refreshing..." : "↻ Refresh"}
                </button>
              </div>

              {auditLoading ? (
                <div className="empty-state">
                  Loading audit records...
                </div>
              ) : auditUnavailable ? (
                <div className="empty-state">
                  <h3>Administrator access required</h3>

                  <p>
                    Your account does not have permission to view audit
                    records. Sign in with an authorized administrator account
                    to access this page.
                  </p>
                </div>
              ) : auditLogs.length === 0 ? (
                <div className="empty-state">
                  <h3>No audit records to display</h3>

                  <p>
                    No records were returned by the audit API. Refresh to
                    check again.
                  </p>
                </div>
              ) : (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>EVENT</th>
                        <th>USER</th>
                        <th>DOCUMENT</th>
                        <th>DATE</th>
                      </tr>
                    </thead>

                    <tbody>
                      {auditLogs.map((log, index) => (
                        <tr key={log.id ?? index}>
                          <td>
                            <span className="category-pill">
                              {log.action ||
                                log.eventType ||
                                log.event ||
                                "Activity"}
                            </span>
                          </td>

                          <td>
                            {log.username ||
                              log.actorUsername ||
                              log.user ||
                              "—"}
                          </td>

                          <td>
                            {log.documentId ?? log.resourceId ?? "—"}
                          </td>

                          <td className="date-cell">
                            {formatDate(
                              log.createdAt ||
                                log.timestamp ||
                                log.eventTime
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              <div className="panel-footer">
                <span>
                  Audit records are subject to backend access controls.
                </span>

                <span className="footer-lock">
                  ♧ Security audit
                </span>
              </div>
            </section>
          )}

          {/* SETTINGS PAGE */}

          {activePage === "Settings" && (
            <>
              <div className="stats-grid">
                <div className="stat-card">
                  <div className="stat-top">
                    <span>Account</span>
                    <div className="stat-icon blue">♙</div>
                  </div>

                  <div className="stat-number stat-user">
                    {authenticatedUser}
                  </div>

                  <div className="stat-foot">
                    Current signed-in username
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-top">
                    <span>My documents</span>
                    <div className="stat-icon purple">▤</div>
                  </div>

                  <div className="stat-number">{documents.length}</div>

                  <div className="stat-foot">
                    Documents owned by your account
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-top">
                    <span>Workspace</span>
                    <div className="stat-icon green">✓</div>
                  </div>

                  <div className="stat-number">Active</div>

                  <div className="stat-foot">
                    Authenticated session
                  </div>
                </div>
              </div>

              <section className="documents-panel">
                <div className="panel-heading">
                  <div>
                    <h2>Account information</h2>
                    <p>
                      Current session details and security guidance.
                    </p>
                  </div>
                </div>

                <div className="table-wrap">
                  <table>
                    <tbody>
                      <tr>
                        <th>Username</th>
                        <td>{authenticatedUser}</td>
                      </tr>

                      <tr>
                        <th>Session status</th>
                        <td>
                          <span className="access-pill">
                            <span /> Signed in
                          </span>
                        </td>
                      </tr>

                      <tr>
                        <th>Authentication</th>
                        <td>HTTP Basic authentication</td>
                      </tr>

                      <tr>
                        <th>Document access</th>
                        <td>
                          Controlled by backend permissions
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <div className="empty-state">
                  <h3>Keep your documents secure</h3>

                  <p>
                    Keep your password private. Grant document access only
                    to the users who need it, and revoke access when it is
                    no longer required.
                  </p>

                  <button
                    className="primary-button"
                    onClick={handleLogout}
                  >
                    Sign out securely
                  </button>
                </div>
              </section>
            </>
          )}

          <footer className="page-footer">
            <span>© 2026 TrustVault</span>
            <span>Secure document management</span>
          </footer>
        </section>
      </main>

      {/* UPLOAD DOCUMENT MODAL */}

      {showUpload && (
        <div
          className="modal-backdrop"
          onClick={(event) => {
            if (
              event.target === event.currentTarget &&
              !uploading
            ) {
              setShowUpload(false);
            }
          }}
        >
          <form className="upload-modal" onSubmit={handleUpload}>
            <div className="modal-heading">
              <div>
                <h2>Upload document</h2>
                <p>
                  Add a document to your secure workspace.
                </p>
              </div>

              <button
                type="button"
                className="modal-close"
                disabled={uploading}
                onClick={() => setShowUpload(false)}
                aria-label="Close upload dialog"
              >
                ×
              </button>
            </div>

            <label htmlFor="documentName">Document name</label>

            <input
              id="documentName"
              value={documentName}
              onChange={(event) =>
                setDocumentName(event.target.value)
              }
              maxLength={200}
              placeholder="e.g. Project Requirements"
              required
            />

            <label htmlFor="category">Category</label>

            <input
              id="category"
              value={category}
              onChange={(event) =>
                setCategory(event.target.value)
              }
              maxLength={100}
              required
            />

            <label htmlFor="documentFile">Choose file</label>

            <input
              id="documentFile"
              type="file"
              onChange={(event) =>
                setFile(event.target.files?.[0] || null)
              }
              accept=".pdf,.txt,.png,.jpg,.jpeg,.docx"
              required
            />

            <p className="upload-hint">
              Allowed types: PDF, TXT, PNG, JPG, JPEG, DOCX.
              Maximum 10 MB.
            </p>

            <div className="modal-actions">
              <button
                type="button"
                className="filter-button"
                disabled={uploading}
                onClick={() => setShowUpload(false)}
              >
                Cancel
              </button>

              <button
                type="submit"
                className="primary-button"
                disabled={uploading}
              >
                {uploading
                  ? "Uploading..."
                  : "Upload securely"}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* DOCUMENT PERMISSIONS MODAL */}

      {selectedDoc && (
        <div
          className="modal-backdrop"
          onClick={(event) => {
            if (
              event.target === event.currentTarget &&
              !sharingLoading
            ) {
              setSelectedDoc(null);
            }
          }}
        >
          <section
            className="upload-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="sharing-dialog-title"
          >
            <div className="modal-heading">
              <div>
                <h2 id="sharing-dialog-title">
                  Manage document access
                </h2>

                <p>{selectedDoc.name}</p>
              </div>

              <button
                type="button"
                className="modal-close"
                disabled={sharingLoading}
                onClick={() => setSelectedDoc(null)}
                aria-label="Close permissions dialog"
              >
                ×
              </button>
            </div>

            <form onSubmit={grantAccess}>
              <label htmlFor="shareUsername">Username</label>

              <input
                id="shareUsername"
                value={targetUsername}
                onChange={(event) =>
                  setTargetUsername(event.target.value)
                }
                placeholder="Enter an existing username"
                maxLength={100}
                required
              />

              <label htmlFor="sharePermission">
                Permission level
              </label>

              <select
                id="sharePermission"
                value={permissionLevel}
                onChange={(event) =>
                  setPermissionLevel(event.target.value)
                }
              >
                <option value="READ">
                  READ — View document
                </option>

                <option value="EDIT">
                  EDIT — Edit access
                </option>
              </select>

              <div className="modal-actions">
                <button
                  type="submit"
                  className="primary-button"
                  disabled={sharingLoading}
                >
                  {sharingLoading
                    ? "Please wait..."
                    : "Grant access"}
                </button>
              </div>
            </form>

            <div className="sharing-list">
              <h3>Users with access</h3>

              {sharingLoading ? (
                <p>Loading permissions...</p>
              ) : permissions.length === 0 ? (
                <p>
                  No users have been granted access yet.
                </p>
              ) : (
                permissions.map((item) => (
                  <div
                    className="sharing-user"
                    key={item.username}
                  >
                    <div>
                      <strong>{item.username}</strong>

                      <p>
                        {item.permission} ·{" "}
                        {item.grantedAt
                          ? new Date(
                              item.grantedAt
                            ).toLocaleString()
                          : "Date unavailable"}
                      </p>
                    </div>

                    <button
                      type="button"
                      className="filter-button"
                      disabled={sharingLoading}
                      onClick={() =>
                        revokeAccess(item.username)
                      }
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