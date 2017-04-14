package bt_week09.dao;

import org.springframework.data.repository.CrudRepository;
import bt_week09.model.Post;

public interface PostRepository extends CrudRepository<Post, Integer> {

}
