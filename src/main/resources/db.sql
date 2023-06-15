DROP TABLE IF EXISTS USR_SESSION;
DROP TABLE IF EXISTS GROUPS;

CREATE TABLE USR_SESSION(ID INT PRIMARY KEY AUTO_INCREMENT, ACCESS_TOKEN VARCHAR(255));
CREATE TABLE GROUPS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), REPOSITORY_URL VARCHAR(255), REPOSITORY_OWNER VARCHAR(255), REPOSITORY_NAME VARCHAR(255));

INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('community-senac', 'https://github.com/Brenno55/community-senac', 'Brenno55', 'community-senac');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('Restaurante', 'https://github.com/denis-lemon/Restaurante', 'denis-lemon', 'Restaurante');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('PI-Gerenciamneto-de-escola', 'https://github.com/Tmpavezzi/PI-Gerenciamneto-de-escola', 'Tmpavezzi', 'PI-Gerenciamneto-de-escola');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('gerenciador_de_notas', 'https://github.com/Danmqest/gerenciador_de_notas', 'Danmqest', 'gerenciador_de_notas');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('classificados-de-carros', 'https://github.com/GuiMikami/classificados-de-carros', 'GuiMikami', 'classificados-de-carros');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('e-coffe', 'https://github.com/Guiisf/e-coffe', 'Guiisf', 'e-coffe');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('projetoacademiapi3', 'https://github.com/carllopessil/projetoacademiapi3', 'carllopessil', 'projetoacademiapi3');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('residup', 'https://github.com/CleitonRSilvaa/residup', 'CleitonRSilvaa', 'residup');