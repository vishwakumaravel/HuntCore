import { Navigate, Route, Routes } from "react-router-dom";
import { Layout } from "./components/Layout";
import { MatchesPage } from "./pages/MatchesPage";
import { OverviewPage } from "./pages/OverviewPage";
import { PlayerPage } from "./pages/PlayerPage";
import { PlayersPage } from "./pages/PlayersPage";

function App() {
  return (
    <Routes>
      <Route element={<Layout />} path="/">
        <Route element={<OverviewPage />} index />
        <Route element={<MatchesPage />} path="matches" />
        <Route element={<PlayersPage />} path="players" />
        <Route element={<PlayerPage />} path="players/:playerName" />
        <Route element={<Navigate replace to="/" />} path="*" />
      </Route>
    </Routes>
  );
}

export default App;
