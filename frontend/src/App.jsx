import { DashboardProvider } from './context/DashboardContext'
import Dashboard from './components/Dashboard/Dashboard'
import styles from './App.module.css'

function App() {
  return (
    <DashboardProvider>
      <div className={styles.app}>
        <Dashboard />
      </div>
    </DashboardProvider>
  )
}

export default App
