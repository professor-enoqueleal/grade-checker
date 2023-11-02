DROP TABLE IF EXISTS USR_SESSION;
DROP TABLE IF EXISTS GROUPS;
DROP TABLE IF EXISTS MEMBERS;

--CREATE TABLE USR_SESSION(ID INT PRIMARY KEY AUTO_INCREMENT, ACCESS_TOKEN VARCHAR(255));
CREATE TABLE GROUPS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), REPOSITORY_URL VARCHAR(255), REPOSITORY_OWNER VARCHAR(255), REPOSITORY_NAME VARCHAR(255));
CREATE TABLE MEMBERS(ID INT PRIMARY KEY AUTO_INCREMENT, NAME VARCHAR(255), GITHUB_USERNAME VARCHAR(255), GROUP_ID INT, FOREIGN KEY (GROUP_ID) REFERENCES GROUPS (ID));

--# TURMA B
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('PetLink', 'https://github.com/EduardoFersilva/PetLink', 'EduardoFersilva', 'PetLink');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('petmatch', 'https://github.com/2D-nz/petmatch', '2D-nz', 'petmatch');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('gerenciamento-locacao', 'https://github.com/Torrez0/gerenciamento-locacao', 'Torrez0', 'gerenciamento-locacao');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('assistente-de-montagem-de-pc', 'https://github.com/Eliseu-F/assistente-de-montagem-de-pc', 'Eliseu-F', 'assistente-de-montagem-de-pc');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('PlataformaDeReceitas', 'https://github.com/Koji-desu/PlataformaDeReceitas', 'Koji-desu', 'PlataformaDeReceitas');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('loja_de_vinhos_stadscas3nb', 'https://github.com/Kleverton-Patrick/loja_de_vinhos_stadscas3nb', 'Kleverton-Patrick', 'loja_de_vinhos_stadscas3nb');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('mapeamento_assalto', 'https://github.com/NathaliaRobson/mapeamento_assalto', 'NathaliaRobson', 'mapeamento_assalto');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('PI3_Projeto', 'https://github.com/eolnardo/PI3_Projeto', 'eolnardo', 'PI3_Projeto');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('LocadoradeVeiculos', 'https://github.com/LeticiaQueiroz2004/LocadoradeVeiculos', 'LeticiaQueiroz2004', 'LocadoradeVeiculos');
INSERT INTO GROUPS (NAME, REPOSITORY_URL, REPOSITORY_OWNER, REPOSITORY_NAME) VALUES ('Redes-de-academias', 'https://github.com/Filriber/Redes-de-academias', 'Filriber', 'Redes-de-academias');