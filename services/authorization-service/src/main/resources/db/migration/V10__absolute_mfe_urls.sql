UPDATE panel SET remote_entry_path = CASE code
  WHEN 'ADMIN' THEN 'http://localhost:3001/remoteEntry.js'
  WHEN 'HR' THEN 'http://localhost:3002/remoteEntry.js'
  WHEN 'FINANCE' THEN 'http://localhost:3003/remoteEntry.js'
  WHEN 'REPORTS' THEN 'http://localhost:3004/remoteEntry.js'
  ELSE remote_entry_path
END
WHERE code IN ('ADMIN', 'HR', 'FINANCE', 'REPORTS');
