package br.com.gradechecker.service;

import br.com.gradechecker.model.Group;

public interface GroupService {

    public Group getGroup(Long groupId, String accessToken);

}
