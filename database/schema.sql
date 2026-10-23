CREATE DATABASE IF NOT EXISTS student_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE student_hub;
CREATE TABLE IF NOT EXISTS students (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100) NOT NULL, email VARCHAR(190) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, role ENUM('STUDENT','ADMIN') NOT NULL DEFAULT 'STUDENT',
 skills VARCHAR(500) NOT NULL DEFAULT '', interests VARCHAR(500) NOT NULL DEFAULT '', bio VARCHAR(1000) NOT NULL DEFAULT '',
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS projects (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, title VARCHAR(150) NOT NULL,
 description TEXT NOT NULL, requirements TEXT NOT NULL, category VARCHAR(80) NOT NULL,
 technologies VARCHAR(500) NOT NULL, status ENUM('OPEN','IN_PROGRESS','COMPLETED','ARCHIVED') NOT NULL DEFAULT 'OPEN',
 capacity INT NOT NULL DEFAULT 5, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (owner_id) REFERENCES students(id), CHECK(capacity BETWEEN 1 AND 100), INDEX(status,category)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS project_members (
 project_id BIGINT NOT NULL, student_id BIGINT NOT NULL, team_role VARCHAR(30) NOT NULL DEFAULT 'MEMBER',
 joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(project_id,student_id),
 FOREIGN KEY(project_id) REFERENCES projects(id), FOREIGN KEY(student_id) REFERENCES students(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS team_requests (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, project_id BIGINT NOT NULL, sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL,
 status ENUM('PENDING','ACCEPTED','REJECTED') NOT NULL DEFAULT 'PENDING', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 responded_at TIMESTAMP NULL,
 pending_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN 1 ELSE NULL END) STORED,
 UNIQUE KEY unique_pending(project_id,sender_id,recipient_id,pending_guard), INDEX(recipient_id,status),
 FOREIGN KEY(project_id) REFERENCES projects(id), FOREIGN KEY(sender_id) REFERENCES students(id), FOREIGN KEY(recipient_id) REFERENCES students(id),
 CHECK(sender_id<>recipient_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS tasks (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, project_id BIGINT NOT NULL, assignee_id BIGINT NOT NULL,
 title VARCHAR(180) NOT NULL, description TEXT NOT NULL, priority ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'MEDIUM',
 status ENUM('PENDING','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'PENDING', deadline DATE NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(project_id,assignee_id) REFERENCES project_members(project_id,student_id), INDEX(assignee_id,status)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS notifications (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, recipient_id BIGINT NOT NULL, message VARCHAR(600) NOT NULL, type VARCHAR(40) NOT NULL,
 project_id BIGINT NULL, request_id BIGINT NULL, is_read BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(recipient_id) REFERENCES students(id),
 FOREIGN KEY(project_id) REFERENCES projects(id), FOREIGN KEY(request_id) REFERENCES team_requests(id), INDEX(recipient_id,is_read)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS discussions (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, project_id BIGINT NOT NULL, author_id BIGINT NOT NULL, message TEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(project_id,author_id) REFERENCES project_members(project_id,student_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS project_files (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, project_id BIGINT NOT NULL, uploader_id BIGINT NOT NULL,
 original_name VARCHAR(200) NOT NULL, stored_name VARCHAR(80) NOT NULL UNIQUE, content_type VARCHAR(100) NOT NULL,
 size_bytes BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(project_id,uploader_id) REFERENCES project_members(project_id,student_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS badges (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(80) NOT NULL, description VARCHAR(300) NOT NULL, points INT NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS student_achievements (
 student_id BIGINT NOT NULL, badge_id BIGINT NOT NULL, earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(student_id,badge_id), FOREIGN KEY(student_id) REFERENCES students(id), FOREIGN KEY(badge_id) REFERENCES badges(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS announcements (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, author_id BIGINT NOT NULL, message VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(author_id) REFERENCES students(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS activity_logs (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, student_id BIGINT NOT NULL, action VARCHAR(100) NOT NULL, project_id BIGINT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(student_id) REFERENCES students(id), FOREIGN KEY(project_id) REFERENCES projects(id)
) ENGINE=InnoDB;
INSERT IGNORE INTO badges(code,name,description,points) VALUES
 ('BUILDER','Project Builder','Created your first project.',20),
 ('TEAM_PLAYER','Team Player','Joined a project through an accepted invitation.',15),
 ('FINISHER','Task Finisher','Completed at least one assigned task.',25),
 ('CONTRIBUTOR','Conversation Starter','Posted your first project discussion.',10);
