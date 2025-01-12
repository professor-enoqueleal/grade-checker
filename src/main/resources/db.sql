DROP TABLE IF EXISTS USR_SESSION;

DROP TABLE IF EXISTS MEMBERS;
DROP TABLE IF EXISTS CONTRIBUTORS;
DROP TABLE IF EXISTS GROUPS;

--CREATE TABLE USR_SESSION(ID INT PRIMARY KEY AUTO_INCREMENT, ACCESS_TOKEN VARCHAR(255));
CREATE TABLE GROUPS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), REPOSITORY_URL VARCHAR(255), REPOSITORY_OWNER VARCHAR(255), REPOSITORY_NAME VARCHAR(255), REPOSITORY_PRIVATE BOOLEAN DEFAULT FALSE);
CREATE TABLE MEMBERS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), GITHUB_USERNAME VARCHAR(255), GROUP_ID INT, FOREIGN KEY (GROUP_ID) REFERENCES GROUPS (ID));
CREATE TABLE CONTRIBUTORS(ID INT, LOGIN VARCHAR(255), AVATAR_URL VARCHAR(255), HTML_URL VARCHAR(255), CONTRIBUTIONS INT, GROUP_ID INT, FOREIGN KEY (GROUP_ID) REFERENCES GROUPS (ID));

--# | name | url | owner | repo
-- INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('instituto-ensino', 'https://github.com/yanaguiii/instituto-ensino', 'yanaguiii', 'instituto-ensino');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('pizzaria-bhg', 'https://github.com/biaxsantana/pizzaria-bhg', 'biaxsantana', 'pizzaria-bhg');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('ecommerce-papelaria', 'https://github.com/marianaaslvs/ecommerce-papelaria', 'marianaaslvs', 'ecommerce-papelaria');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('four-bank', 'https://github.com/Rai84/four-bank', 'Rai84', 'four-bank');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('fora-da-fila', 'https://github.com/gabrielparrra/fora-da-fila', 'gabrielparrra', 'fora-da-fila');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('senac_3sem_PI-III', 'https://github.com/LeonardoDSSilva/senac_3sem_PI-III', 'LeonardoDSSilva', 'senac_3sem_PI-III');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('cine-mood', 'https://github.com/Munizzs/cine-mood', 'Munizzs', 'cine-mood');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('animal-side', 'https://github.com/lalaais/animal-side', 'lalaais', 'animal-side');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('fire-farmacy-group', 'https://github.com/KaickSt/fire-farmacy-group', 'KaickSt', 'fire-farmacy-group');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('gppn', 'https://github.com/HugoMeloo/GPPN', 'HugoMeloo', 'GPPN');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('air-planning', 'https://github.com/Bielzin0108/air-planning', 'Bielzin0108', 'air-planning');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('biblioteca-virtual', 'https://github.com/AmorimYago/biblioteca-virtual', 'AmorimYago', 'biblioteca-virtual');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('auto-atendimento-biblioteca', 'https://github.com/victor-delmondes/auto-atendimento-biblioteca', 'victor-delmondes', 'auto-atendimento-biblioteca');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('loja', 'https://github.com/lucassgg1/loja', 'lucassgg1', 'loja');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('ingressos-cinema', 'https://github.com/RobertoJGB/Reserva-Ingressos-Cinema', 'RobertoJGB', 'Reserva-Ingressos-Cinema');
--INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('projeto-integrador', 'https://github.com/Nmoreira07/ProjetoIntegrador', 'Nmoreira07', 'ProjetoIntegrador');
