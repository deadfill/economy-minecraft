-- GravitLauncher tables initialization

-- Users table for authentication
CREATE TABLE IF NOT EXISTS users (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    accesstoken VARCHAR(255),
    serverid VARCHAR(255),
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Hardware ID table for device tracking
CREATE TABLE IF NOT EXISTS hwids (
    id SERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    hwidId VARCHAR(255) NOT NULL,
    banned BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (uuid) REFERENCES users(uuid) ON DELETE CASCADE
);

-- Hardware ID log table for tracking
CREATE TABLE IF NOT EXISTS hwidLog (
    id SERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    hwidId VARCHAR(255) NOT NULL,
    ip_address INET,
    action VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (uuid) REFERENCES users(uuid) ON DELETE CASCADE
);

-- User permissions table
CREATE TABLE IF NOT EXISTS user_permissions (
    uuid VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (uuid) REFERENCES users(uuid) ON DELETE CASCADE
);

-- Create unique index for user permissions
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_permissions_uuid_name 
ON user_permissions (uuid, name);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_accesstoken ON users(accesstoken);
CREATE INDEX IF NOT EXISTS idx_hwids_uuid ON hwids(uuid);
CREATE INDEX IF NOT EXISTS idx_hwids_hwidid ON hwids(hwidId);
CREATE INDEX IF NOT EXISTS idx_user_permissions_uuid ON user_permissions(uuid);

-- Insert default admin user (password: admin123 - bcrypt hashed)
INSERT INTO users (uuid, username, password, email) 
VALUES (
    'f47ac10b-58cc-4372-a567-0e02b2c3d479', 
    'admin', 
    '$2a$10$N9qo8uLOickgx2ZMRZoMye1IeOkWLrJJONrGbPJFDrJJKJ7HZrGIm', 
    'admin@economyminecraft.local'
) ON CONFLICT (uuid) DO NOTHING;

-- Grant admin permissions
INSERT INTO user_permissions (uuid, name) 
VALUES 
    ('f47ac10b-58cc-4372-a567-0e02b2c3d479', 'launchserver.*'),
    ('f47ac10b-58cc-4372-a567-0e02b2c3d479', 'launchserver.profile.*'),
    ('f47ac10b-58cc-4372-a567-0e02b2c3d479', 'launchserver.management.*')
ON CONFLICT (uuid, name) DO NOTHING;

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply trigger to users table
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

COMMIT;
