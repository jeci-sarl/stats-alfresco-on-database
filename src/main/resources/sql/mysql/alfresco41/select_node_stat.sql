select aca.parent_node_id, SUM(acu.content_size) AS dir_local_size, COUNT(acu.content_size) as number_elements
from alf_child_assoc aca
join alf_node_properties anp on anp.node_id = aca.child_node_id
join alf_content_data acd on acd.id = anp.long_value
join alf_content_url acu on acd.content_url_id = acu.id
where anp.actual_type_n=21
group by aca.parent_node_id