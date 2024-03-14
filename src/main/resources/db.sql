DROP TABLE IF EXISTS USR_SESSION;

DROP TABLE IF EXISTS MEMBERS;
DROP TABLE IF EXISTS GROUPS;

--CREATE TABLE USR_SESSION(ID INT PRIMARY KEY AUTO_INCREMENT, ACCESS_TOKEN VARCHAR(255));
CREATE TABLE GROUPS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), REPOSITORY_URL VARCHAR(255), REPOSITORY_OWNER VARCHAR(255), REPOSITORY_NAME VARCHAR(255));
CREATE TABLE MEMBERS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), GITHUB_USERNAME VARCHAR(255), GROUP_ID INT, FOREIGN KEY (GROUP_ID) REFERENCES GROUPS (ID));

--# TURMA B
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('decimo-andar', 'https://github.com/EcthorSilva/decimo-andar', 'EcthorSilva', 'decimo-andar');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('ticketgol', 'https://github.com/MicaelCadete/ticketgol', 'MicaelCadete', 'ticketgol');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('mobile-genius', 'https://github.com/dianalinaemidio/mobile_genius', 'dianalinaemidio', 'mobile_genius');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('funcionalidades-projeto', 'https://github.com/pedroSantosSilva44/funcionalidades-projeto', 'pedroSantosSilva44', 'funcionalidades-projeto');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('imobiliaria', 'https://github.com/NatanSilva31/projeto-resort-imobiliaria', 'NatanSilva31', 'projeto-resort-imobiliaria');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('reservas-para-restaurantes', 'https://github.com/IgorPaes/reservas-para-restaurantes', 'IgorPaes', 'reservas-para-restaurantes');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('qualiset', 'https://github.com/EduardoXerxenevsky/qualiset', 'EduardoXerxenevsky', 'qualiset');

