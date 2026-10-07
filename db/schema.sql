CREATE DATABASE IF NOT EXISTS campusos CHARACTER SET utf8mb4;
USE campusos;

CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  email VARCHAR(120) NOT NULL UNIQUE,
  password_hash CHAR(64) NOT NULL,
  role ENUM('student','admin') DEFAULT 'student',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- one row per student per subject: attendance + marks + assignment/quiz scores
CREATE TABLE subjects (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  present INT DEFAULT 0,
  total INT DEFAULT 0,
  marks DOUBLE DEFAULT 0,
  assign_pct DOUBLE DEFAULT 0,
  quiz_pct DOUBLE DEFAULT 0,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE timetable (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  day_of_week TINYINT NOT NULL,           -- 1 = Monday ... 7 = Sunday
  start_time TIME NOT NULL,
  title VARCHAR(80) NOT NULL,
  subject_id BIGINT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE SET NULL
);

CREATE TABLE assignments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  subject_id BIGINT NULL,
  title VARCHAR(120) NOT NULL,
  deadline DATE NOT NULL,
  difficulty TINYINT DEFAULT 3,           -- 1..5
  weightage DOUBLE DEFAULT 10,            -- marks weightage
  done BOOLEAN DEFAULT FALSE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE SET NULL
);

CREATE TABLE exams (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  subject VARCHAR(80) NOT NULL,
  exam_date DATE NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE exam_topics (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  exam_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  weak BOOLEAN DEFAULT FALSE,
  FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE
);

CREATE TABLE expenses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  category VARCHAR(30) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  note VARCHAR(120),
  spent_on DATE NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE lost_found (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type ENUM('lost','found') NOT NULL,
  title VARCHAR(100) NOT NULL,
  category VARCHAR(40),
  color VARCHAR(30),
  location VARCHAR(80),
  item_date DATE NOT NULL,
  description VARCHAR(255),
  status ENUM('open','resolved') DEFAULT 'open',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE marketplace (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  seller_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  price DECIMAL(10,2) DEFAULT 0,
  item_condition ENUM('new','good','fair') DEFAULT 'good',
  category VARCHAR(40),
  listing_type ENUM('sell','exchange','free') DEFAULT 'sell',
  status ENUM('open','closed') DEFAULT 'open',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE questions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(150) NOT NULL,
  body VARCHAR(600),
  upvotes INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE answers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  body VARCHAR(600) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE question_votes (
  question_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (question_id, user_id)
);
