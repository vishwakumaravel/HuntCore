import { NavLink, Outlet } from "react-router-dom";
import { publicApi } from "../api";

const links = [
  { to: "/", label: "Overview", end: true },
  { to: "/matches", label: "Matches" },
  { to: "/players", label: "Players" }
];

export function Layout() {
  return (
    <div className="app-shell">
      <div className="background-glow background-glow-a" />
      <div className="background-glow background-glow-b" />
      <header className="hero">
        <div className="hero-copy">
          <p className="eyebrow">HuntCore Live Ops</p>
          <h1>Match history, player ladders, and live hunt state in one place.</h1>
          <p className="hero-text">
            This dashboard reads the public HuntCore API directly. It is built to
            stay static-host friendly while the Paper server and backend keep the
            game and stats authoritative.
          </p>
        </div>
        <div className="hero-meta">
          <div className="hero-chip">
            <span className="chip-label">API base</span>
            <span className="chip-value">{publicApi.apiBaseUrl}</span>
          </div>
          <div className="hero-chip">
            <span className="chip-label">Frontend target</span>
            <span className="chip-value">Cloudflare Pages</span>
          </div>
        </div>
      </header>

      <nav aria-label="Primary" className="top-nav">
        {links.map((link) => (
          <NavLink
            key={link.to}
            className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}
            end={link.end}
            to={link.to}
          >
            {link.label}
          </NavLink>
        ))}
      </nav>

      <main className="content-shell">
        <Outlet />
      </main>
    </div>
  );
}
